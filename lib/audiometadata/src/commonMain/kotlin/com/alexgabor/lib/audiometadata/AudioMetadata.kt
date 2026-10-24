package com.alexgabor.lib.audiometadata

import kotlin.time.Duration

/** Formats [AudioMetadataReader] recognises from their content. */
enum class AudioFormat {
    Mp3,
}

/** What one audio file says about itself. Anything it doesn't say is `null` or empty. */
data class AudioMetadata(
    val format: AudioFormat,
    val tags: AudioTags = AudioTags(),
    val duration: Duration? = null,
    val cover: Cover? = null,
    /** Top-level chapters in playback order. Sections are chapters with [Chapter.children]. */
    val chapters: List<Chapter> = emptyList(),
)

data class AudioTags(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val narrator: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
)

/** An embedded picture. Not a data class, so equality compares the bytes rather than the array. */
class Cover(val data: ByteArray, val mimeType: String) {
    override fun equals(other: Any?): Boolean =
        other is Cover && mimeType == other.mimeType && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * data.contentHashCode() + mimeType.hashCode()

    override fun toString(): String = "Cover(mimeType=$mimeType, ${data.size} bytes)"
}

/**
 * A playable range of the file, or, when it has [children], a section grouping them. A section's
 * [start] and [end] span its children.
 */
data class Chapter(
    val title: String?,
    val start: Duration,
    val end: Duration,
    val children: List<Chapter> = emptyList(),
)
