package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoMediaTypeAudio
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyMediaType
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusNoActionableNowPlayingItem
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

/**
 * The lock screen's and Control Center's Now Playing for [player]: what it shows comes from [PanelInfo], and the
 * remote commands (also what headset buttons send) go to [player] like the app's own buttons do.
 *
 * Now Playing moves the elapsed time on by itself from the rate it was given, so [PanelUpdates] only sends it
 * again when that would be off: on play and pause, a seek, a speed or chapter change.
 */
@OptIn(ExperimentalForeignApi::class)
internal class NowPlayingPanel(private val player: BookPlayer) {

    private val updates = PanelUpdates()

    /** The artwork for [artworkPath], made once per cover. */
    private var artwork: MPMediaItemArtwork? = null
    private var artworkPath: String? = null

    /** Registers the remote commands and follows [player]'s state, on the main thread, for as long as [scope]. */
    fun start(scope: CoroutineScope) {
        scope.launch(CoroutineDispatchers.Main) {
            registerCommands()
            player.state
                .map { it.panelInfo() }
                .filter(updates::shouldPush)
                .collect(::show)
        }
    }

    private fun show(info: PanelInfo?) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        if (info == null) {
            center.nowPlayingInfo = null
            center.playbackState = MPNowPlayingPlaybackStateStopped
            return
        }
        val values = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to info.title,
            MPMediaItemPropertyAlbumTitle to info.title,
            MPMediaItemPropertyPlaybackDuration to info.duration.toDouble(DurationUnit.SECONDS),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to info.position.toDouble(DurationUnit.SECONDS),
            MPNowPlayingInfoPropertyPlaybackRate to if (info.isPlaying) info.speed.toDouble() else 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate to info.speed.toDouble(),
            MPNowPlayingInfoPropertyMediaType to MPNowPlayingInfoMediaTypeAudio,
        )
        info.subtitle?.let { values[MPMediaItemPropertyArtist] = it }
        artworkFor(info.coverPath)?.let { values[MPMediaItemPropertyArtwork] = it }
        center.nowPlayingInfo = values
        center.playbackState = if (info.isPlaying) MPNowPlayingPlaybackStatePlaying else MPNowPlayingPlaybackStatePaused
    }

    private fun artworkFor(path: String?): MPMediaItemArtwork? {
        if (path != artworkPath) {
            artworkPath = path
            artwork = path?.let { UIImage.imageWithContentsOfFile(it) }?.let { image ->
                MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
            }
        }
        return artwork
    }

    private fun registerCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.handle { player.play() }
        center.pauseCommand.handle { player.pause() }
        center.togglePlayPauseCommand.handle { if (player.state.value.isPlaying) player.pause() else player.play() }
        center.skipBackwardCommand.preferredIntervals = listOf(SkipInterval.toDouble(DurationUnit.SECONDS))
        center.skipBackwardCommand.handle { player.skipBack() }
        center.skipForwardCommand.preferredIntervals = listOf(SkipInterval.toDouble(DurationUnit.SECONDS))
        center.skipForwardCommand.handle { player.skipForward() }
        center.changePlaybackPositionCommand.handle { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: return@handle
            player.seekTo(position.seconds)
        }
        // A book has no tracks; headsets' next/previous arrive as these and do nothing.
        listOf(
            center.nextTrackCommand,
            center.previousTrackCommand,
            center.seekForwardCommand,
            center.seekBackwardCommand,
            center.changePlaybackRateCommand,
        ).forEach { it.enabled = false }
    }

    /** Enables this command and runs [action] for it, when a book is loaded. */
    private fun MPRemoteCommand.handle(action: (MPRemoteCommandEvent) -> Unit) {
        enabled = true
        addTargetWithHandler { event ->
            if (player.state.value.book == null || event == null) {
                MPRemoteCommandHandlerStatusNoActionableNowPlayingItem
            } else {
                action(event)
                MPRemoteCommandHandlerStatusSuccess
            }
        }
    }
}
