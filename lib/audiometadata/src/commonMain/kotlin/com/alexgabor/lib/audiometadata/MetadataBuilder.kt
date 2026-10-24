package com.alexgabor.lib.audiometadata

import kotlin.time.Duration

/**
 * Collects what a parser finds as it goes, so whatever was read before a failure still ends up in the
 * result. Every field keeps the first value offered: parsers offer their preferred source first.
 */
internal class MetadataBuilder(private val format: AudioFormat) {
    var title: String? = null; set(value) { if (field == null) field = value.clean() }
    var album: String? = null; set(value) { if (field == null) field = value.clean() }
    var artist: String? = null; set(value) { if (field == null) field = value.clean() }
    var albumArtist: String? = null; set(value) { if (field == null) field = value.clean() }
    var narrator: String? = null; set(value) { if (field == null) field = value.clean() }
    var composer: String? = null; set(value) { if (field == null) field = value.clean() }
    var genre: String? = null; set(value) { if (field == null) field = value.clean() }
    var year: Int? = null; set(value) { if (field == null) field = value.positive() }
    var trackNumber: Int? = null; set(value) { if (field == null) field = value.positive() }
    var trackTotal: Int? = null; set(value) { if (field == null) field = value.positive() }
    var discNumber: Int? = null; set(value) { if (field == null) field = value.positive() }
    var discTotal: Int? = null; set(value) { if (field == null) field = value.positive() }
    var duration: Duration? = null; set(value) { if (field == null && value != null && value.isPositive()) field = value }
    var chapters: List<Chapter> = emptyList()

    private var cover: Cover? = null
    private var coverIsFront = false

    /** Keeps the first picture, unless a front cover turns up later. */
    fun offerCover(cover: Cover, isFront: Boolean) {
        if (cover.data.isEmpty()) return
        if (this.cover == null || (isFront && !coverIsFront)) {
            this.cover = cover
            coverIsFront = isFront
        }
    }

    fun build() = AudioMetadata(
        format = format,
        tags = AudioTags(
            title = title,
            album = album,
            artist = artist,
            albumArtist = albumArtist,
            narrator = narrator,
            composer = composer,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            trackTotal = trackTotal,
            discNumber = discNumber,
            discTotal = discTotal,
        ),
        duration = duration,
        cover = cover,
        chapters = chapters,
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Int?.positive(): Int? = this?.takeIf { it > 0 }
}
