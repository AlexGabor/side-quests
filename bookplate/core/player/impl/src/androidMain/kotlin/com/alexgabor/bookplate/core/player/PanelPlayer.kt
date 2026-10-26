@file:OptIn(UnstableApi::class)

package com.alexgabor.bookplate.core.player

import android.net.Uri
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * What the media session shows the system: [player] as it is, but labelled with the [PanelInfo] given to
 * [show] (book title, chapter or author, cover) and without previous/next, since a book is one item and the
 * panel skips 10 seconds instead.
 *
 * The app's own controller talks to the same player, and needs neither the labels nor previous/next.
 */
internal class PanelPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {

    private var panel: PanelInfo? = null

    /** Labels the loaded item with [info]; `null` falls back to the item's own metadata. Main thread only. */
    fun show(info: PanelInfo?) {
        panel = info
        invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val builder = state.buildUpon()
            .setAvailableCommands(state.availableCommands.buildUpon().removeAll(*TrackCommands).build())
        val info = panel
        if (info != null && !state.timeline.isEmpty) {
            val metadata = state.currentMetadata.buildUpon()
                .setTitle(info.title)
                .setDisplayTitle(info.title)
                .setArtist(info.subtitle)
                .setSubtitle(info.subtitle)
                .setAlbumTitle(info.title)
                .setArtworkUri(info.coverPath?.let { Uri.fromFile(File(it)) })
                .build()
            builder.setPlaylist(state.timeline, state.currentTracks, metadata)
        }
        return builder.build()
    }

    private companion object {
        val TrackCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        )
    }
}
