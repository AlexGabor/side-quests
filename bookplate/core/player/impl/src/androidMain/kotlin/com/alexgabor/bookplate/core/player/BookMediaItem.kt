@file:OptIn(UnstableApi::class)

package com.alexgabor.bookplate.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.alexgabor.lib.documentaccess.DocumentRef

/**
 * A book as one media item: its files travel in the item's request extras (they survive the hop from the
 * controller to the session), and [BookMediaSourceFactory] plays them back to back as one window. So the
 * player's position and duration, and the media panel's seek bar, are the whole book's.
 */
internal fun bookMediaItem(files: List<OutputFile>): MediaItem {
    val first = files.firstOrNull()
    val extras = Bundle().apply {
        putStringArray(EXTRA_REFS, files.map { it.ref.asString() }.toTypedArray())
        putLongArray(EXTRA_DURATIONS, files.map { it.duration.inWholeMilliseconds }.toLongArray())
    }
    return MediaItem.Builder()
        .setMediaId(BOOK_MEDIA_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(first?.title)
                .setArtist(first?.artist)
                .setAlbumTitle(first?.title)
                .setDurationMs(files.sumOf { it.duration.inWholeMilliseconds })
                .build(),
        )
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        .build()
}

/** [item] with the private URI the player's source factory recognises, or `null` when it isn't a book. */
internal fun resolveBookItem(item: MediaItem): MediaItem? {
    if (item.mediaId != BOOK_MEDIA_ID || item.files() == null) return null
    return item.buildUpon().setUri(Uri.fromParts(BOOK_SCHEME, BOOK_MEDIA_ID, null)).build()
}

/** Turns a book item into a `ConcatenatingMediaSource2` of its files; anything else goes to [files]. */
internal class BookMediaSourceFactory(private val files: MediaSource.Factory) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val parts = mediaItem.files() ?: return files.createMediaSource(mediaItem)
        val builder = ConcatenatingMediaSource2.Builder().setMediaItem(mediaItem)
        // The parsed duration stands in until each file's own is known.
        parts.forEach { (ref, durationMs) -> builder.add(files.createMediaSource(MediaItem.fromUri(documentUri(ref))), durationMs) }
        return builder.build()
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory =
        apply { files.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply { files.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }

    override fun getSupportedTypes(): IntArray = files.supportedTypes
}

/** The files a book item carries, with their parsed durations in milliseconds. */
private fun MediaItem.files(): List<Pair<DocumentRef, Long>>? {
    val extras = requestMetadata.extras ?: return null
    val refs = extras.getStringArray(EXTRA_REFS) ?: return null
    val durations = extras.getLongArray(EXTRA_DURATIONS) ?: return null
    if (refs.isEmpty() || refs.size != durations.size) return null
    return refs.mapIndexed { index, ref -> (DocumentRef.parse(ref) ?: return null) to durations[index] }
}

private const val BOOK_MEDIA_ID = "book"
private const val BOOK_SCHEME = "bookplate-book"
private const val EXTRA_REFS = "com.alexgabor.bookplate.refs"
private const val EXTRA_DURATIONS = "com.alexgabor.bookplate.durations"
