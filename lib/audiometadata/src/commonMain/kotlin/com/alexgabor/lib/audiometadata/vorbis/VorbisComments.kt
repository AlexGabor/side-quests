package com.alexgabor.lib.audiometadata.vorbis

import com.alexgabor.lib.audiometadata.Chapter
import com.alexgabor.lib.audiometadata.Cover
import com.alexgabor.lib.audiometadata.FlatChapters
import com.alexgabor.lib.audiometadata.Images
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.le32
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** `KEY=value` pairs in file order, keys upper-cased. */
internal class VorbisComments(val entries: List<Pair<String, String>>) {

    /** Every value of [key], in file order. */
    fun values(key: String): List<String> = entries.filter { it.first == key }.map { it.second }

    /** The values of [key] joined with `; `, or `null` when there are none. */
    fun joined(key: String): String? = values(key).map { it.trim() }.filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

    companion object {
        /**
         * The comment list at [start]: vendor string, count, then length-prefixed `KEY=value` entries,
         * all little-endian. A cut-off list keeps the entries before the cut.
         */
        fun parse(data: ByteArray, start: Int = 0): VorbisComments {
            val entries = mutableListOf<Pair<String, String>>()
            var p = start
            if (p + 4 > data.size) return VorbisComments(entries)
            val vendor = data.le32(p)
            if (vendor > data.size - p - 4) return VorbisComments(entries)
            p += 4 + vendor.toInt()
            if (p + 4 > data.size) return VorbisComments(entries)
            // Each entry takes at least its 4-byte length, so the count can't honestly exceed this.
            val count = minOf(data.le32(p), (data.size - p - 4) / 4L)
            p += 4
            for (i in 0 until count) {
                if (p + 4 > data.size) break
                val length = data.le32(p)
                if (length > data.size - p - 4) break
                val text = data.decodeToString(p + 4, p + 4 + length.toInt())
                p += 4 + length.toInt()
                val eq = text.indexOf('=')
                if (eq <= 0) continue
                entries += text.substring(0, eq).uppercase() to text.substring(eq + 1)
            }
            return VorbisComments(entries)
        }

        const val SEPARATOR = "; "
    }
}

/** What Vorbis comments mean, shared by FLAC and Ogg: tags, covers and `CHAPTERnnn` chapters. */
internal object Vorbis {

    fun applyTags(comments: VorbisComments, into: MetadataBuilder) {
        into.title = comments.joined("TITLE")
        into.album = comments.joined("ALBUM")
        into.artist = comments.joined("ARTIST")
        into.albumArtist = comments.joined("ALBUMARTIST") ?: comments.joined("ALBUM ARTIST") ?: comments.joined("ALBUM_ARTIST")
        into.narrator = narrator(comments)
        into.composer = comments.joined("COMPOSER")
        into.genre = comments.joined("GENRE")
        into.year = listOf("DATE", "YEAR", "ORIGINALDATE").firstNotNullOfOrNull { key ->
            comments.values(key).firstNotNullOfOrNull { YEAR.find(it)?.value?.toIntOrNull() }
        }
        number(comments.joined("TRACKNUMBER")) { n, total -> into.trackNumber = n; into.trackTotal = total }
        into.trackTotal = number(comments.joined("TRACKTOTAL") ?: comments.joined("TOTALTRACKS"))
        number(comments.joined("DISCNUMBER")) { n, total -> into.discNumber = n; into.discTotal = total }
        into.discTotal = number(comments.joined("DISCTOTAL") ?: comments.joined("TOTALDISCS"))
    }

    /** `METADATA_BLOCK_PICTURE` (a base64 FLAC picture block) and the legacy `COVERART`. */
    fun applyCovers(comments: VorbisComments, into: MetadataBuilder) {
        for (value in comments.values("METADATA_BLOCK_PICTURE")) {
            try {
                FlacPicture.parse(Base64.decode(value))?.let { into.offerCover(it.cover, it.isFront) }
            } catch (_: Exception) {
                // Skip just this picture.
            }
        }
        val mime = comments.joined("COVERARTMIME")?.lowercase()?.takeIf { '/' in it }
        for (value in comments.values("COVERART")) {
            val image = Base64.decode(value)
            if (image.isNotEmpty()) into.offerCover(Cover(image, mime ?: Images.sniffMimeType(image)), isFront = false)
        }
    }

    /** `CHAPTERnnn=[HH:]MM:SS[.fff]`, titled by `CHAPTERnnnNAME`. Unparseable times are skipped. */
    fun chapters(comments: VorbisComments, duration: Duration?): List<Chapter> {
        val names = HashMap<Int, String>()
        val starts = mutableListOf<Pair<Int, Duration>>()
        for ((key, value) in comments.entries) {
            val match = CHAPTER_KEY.matchEntire(key) ?: continue
            val number = match.groupValues[1].toIntOrNull() ?: continue
            if (match.groupValues[2].isEmpty()) {
                time(value)?.let { starts += number to it }
            } else if (number !in names) {
                names[number] = value
            }
        }
        return FlatChapters.build(starts.map { (n, start) -> FlatChapters.Entry(names[n], start) }, duration)
    }

    /** `HH:MM:SS.fff` or `MM:SS.fff`, any number of fraction digits. */
    fun time(value: String): Duration? {
        val match = TIME.matchEntire(value.trim()) ?: return null
        val (h, m, s, fraction) = match.destructured
        val nanos = fraction.take(9).padEnd(9, '0').toLongOrNull() ?: 0
        return (h.toLongOrNull() ?: 0).hours + (m.toLongOrNull() ?: return null).minutes +
            (s.toLongOrNull() ?: return null).seconds + nanos.nanoseconds
    }

    private fun narrator(comments: VorbisComments): String? {
        val explicit = comments.entries
            .filter { (key, _) -> key.filter { it in 'A'..'Z' } in NARRATOR_KEYS }
            .map { it.second.trim() }.filter { it.isNotEmpty() }
        return explicit.takeIf { it.isNotEmpty() }?.joinToString(VorbisComments.SEPARATOR) ?: comments.joined("PERFORMER")
    }

    private inline fun number(value: String?, set: (Int?, Int?) -> Unit) {
        val parts = value?.split('/') ?: return
        set(parts[0].trim().toIntOrNull(), parts.getOrNull(1)?.trim()?.toIntOrNull())
    }

    private fun number(value: String?): Int? = value?.trim()?.toIntOrNull()

    private val YEAR = Regex("""\d{4}""")
    private val NARRATOR_KEYS = setOf("NARRATOR", "NARRATEDBY")
    private val CHAPTER_KEY = Regex("""CHAPTER(\d+)(NAME)?""")
    private val TIME = Regex("""(?:(\d+):)?(\d+):(\d+)(?:[.,](\d*))?""")
}
