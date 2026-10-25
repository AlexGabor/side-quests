@file:OptIn(UnstableApi::class)

package com.alexgabor.bookplate.core.player

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentRef
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.koin.android.ext.android.inject

/**
 * Owns the app's `ExoPlayer` and its media session, so playback keeps going in the background with Media3's
 * media notification. [Media3AudioOutput] drives it through a `MediaController`; the notification's buttons
 * reach the same player.
 */
class PlaybackService : MediaSessionService() {

    private val documentAccess: DocumentAccess by inject()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DocumentDataSource.Factory(documentAccess), extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val builder = MediaSession.Builder(this, player).setCallback(ResolvingCallback)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Items arrive with a [DocumentRef] as their media id. Their URI is rebuilt here rather than trusted to
     * survive the trip from the controller.
     */
    private object ResolvingCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.mapNotNullTo(mutableListOf()) { item ->
                val ref = DocumentRef.parse(item.mediaId) ?: return@mapNotNullTo null
                item.buildUpon().setUri(documentUri(ref)).build()
            },
        )
    }
}
