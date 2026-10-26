@file:OptIn(UnstableApi::class)

package com.alexgabor.bookplate.core.player

import android.app.PendingIntent
import android.content.Intent
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Owns the app's `ExoPlayer` and its media session, so playback keeps going in the background with Media3's
 * media notification. [Media3AudioOutput] drives it through a `MediaController`; the notification, the lock
 * screen and headset buttons reach the same player, and the engine follows what they do.
 *
 * A book is one item ([bookMediaItem]), so the panel's seek bar is the whole book. [PanelPlayer] labels it with
 * the [BookPlayer]'s book and chapter and offers rewind/forward 10 seconds in place of previous/next.
 */
class PlaybackService : MediaSessionService() {

    private val documentAccess: DocumentAccess by inject()
    private val bookPlayer: BookPlayer by inject()
    private val inkColors: PanelInkColors by inject()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val files = DefaultMediaSourceFactory(DocumentDataSource.Factory(documentAccess), extractors)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(BookMediaSourceFactory(files))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SkipInterval.inWholeMilliseconds)
            .setSeekForwardIncrementMs(SkipInterval.inWholeMilliseconds)
            .build()
        val player = PanelPlayer(exoPlayer, PanelArtworkPng(inkColors))
        val builder = MediaSession.Builder(this, player)
            .setCallback(Callback)
            .setMediaButtonPreferences(skipButtons)
        packageManager.getLaunchIntentForPackage(packageName)?.component?.let { activity ->
            val showPlaying = Intent(ACTION_SHOW_PLAYING)
                .setComponent(activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, showPlaying, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
        // Media3 keeps a paused service in the foreground for 10 minutes, and its notification can't be swiped
        // away until then. A paused book leaves the foreground at once, so the notification can be dismissed.
        setForegroundServiceTimeoutMs(0)

        lifecycleScope.launch(CoroutineDispatchers.Main) {
            bookPlayer.state
                .map { it.panelInfo() }
                // Only what the labels show; the position comes from the player itself.
                .distinctUntilChangedBy { info -> info?.run { listOf(bookId, title, subtitle, coverPath, inks) } }
                .collect(player::show)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Media3 hides a paused notification the user dismissed but leaves the service started. Stop it: it is no
     * longer needed until the app plays again, which starts it again through its controller. The app's
     * controller may still be bound, so the session stays until it lets go.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val dismissed = intent?.getBooleanExtra(NOTIFICATION_DISMISSED, false) == true
        if (dismissed && session?.player?.playWhenReady != true) stopSelf()
        return result
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private object Callback : MediaSession.Callback {

        /**
         * A book item arrives with its files in its extras. Its URI is set here rather than trusted to survive
         * the trip from the controller.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNullTo(mutableListOf(), ::resolveBookItem))

        /**
         * Headsets' next/previous and fast-forward/rewind skip 10 seconds: a book has no next track. Play and
         * pause buttons keep Media3's handling.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
            val skip: (Player) -> Unit = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Player::seekForward
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> Player::seekBack
                else -> return false
            }
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) skip(session.player)
            return true
        }
    }

    private companion object {
        /** The extra Media3 sets on the intent its notification sends when it is dismissed. */
        const val NOTIFICATION_DISMISSED = "androidx.media3.session.NOTIFICATION_DISMISSED_EVENT_KEY"

        /** In the panel's previous/next places. */
        val skipButtons = listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName("Rewind 10 seconds")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName("Forward 10 seconds")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }
}

/**
 * The action of the intent the media notification opens the app with: show the playing book in Player. It
 * goes to the launcher activity by name, so it needs no intent filter, and carries no data.
 */
const val ACTION_SHOW_PLAYING = "com.alexgabor.bookplate.action.SHOW_PLAYING"
