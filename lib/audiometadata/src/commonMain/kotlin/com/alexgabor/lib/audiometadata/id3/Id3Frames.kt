package com.alexgabor.lib.audiometadata.id3

import com.alexgabor.lib.audiometadata.Cover
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.u32
import com.alexgabor.lib.audiometadata.u8

/** What ID3v2 frames mean: text, `TXXX`, pictures, `CHAP` and `CTOC`. */
internal object Id3Frames {

    /**
     * Applies one tag's [frames] to [into] and collects its chapter frames into [chapters]. Each frame is
     * decoded on its own, so a broken one doesn't stop the rest.
     */
    fun apply(frames: List<Id3Frame>, major: Int, into: MetadataBuilder, chapters: Id3Chapters) {
        // The date frames are ranked, not read in file order.
        val years = arrayOfNulls<Int>(YEAR_FRAMES.size)
        for (frame in frames) {
            try {
                applyFrame(frame, major, into, chapters, years)
            } catch (_: Exception) {
                // Skip just this frame.
            }
        }
        years.firstOrNull { it != null }?.let { into.year = it }
    }

    private fun applyFrame(frame: Id3Frame, major: Int, into: MetadataBuilder, chapters: Id3Chapters, years: Array<Int?>) {
        val yearRank = YEAR_FRAMES.indexOf(frame.id)
        if (yearRank >= 0) {
            if (years[yearRank] == null) years[yearRank] = text(frame)?.let(::year)
            return
        }
        when (frame.id) {
            "TIT2" -> into.title = text(frame)
            "TALB" -> into.album = text(frame)
            "TPE1" -> into.artist = text(frame)
            "TPE2" -> into.albumArtist = text(frame)
            "TCOM" -> into.composer = text(frame)
            "TCON" -> into.genre = Id3Text.values(frame.data).mapNotNull(Genres::resolve).distinct().joinToString(SEPARATOR)
            "TRCK" -> number(frame) { n, total -> into.trackNumber = n; into.trackTotal = total }
            "TPOS" -> number(frame) { n, total -> into.discNumber = n; into.discTotal = total }
            "TXXX" -> userText(frame, into)
            "APIC" -> picture(frame.data, v22 = false, into)
            "PIC" -> picture(frame.data, v22 = true, into)
            "CHAP" -> chapter(frame.data, major)?.let(chapters::add)
            "CTOC" -> tableOfContents(frame.data, major)?.let(chapters::add)
        }
    }

    private fun text(frame: Id3Frame): String? =
        Id3Text.values(frame.data).takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

    private fun year(value: String): Int? = YEAR.find(value)?.value?.toIntOrNull()

    private inline fun number(frame: Id3Frame, set: (Int?, Int?) -> Unit) {
        val value = Id3Text.values(frame.data).firstOrNull() ?: return
        val parts = value.split('/')
        set(parts[0].trim().toIntOrNull(), parts.getOrNull(1)?.trim()?.toIntOrNull())
    }

    private fun userText(frame: Id3Frame, into: MetadataBuilder) {
        val data = frame.data
        if (data.isEmpty()) return
        val encoding = data.u8(0)
        val (description, valueStart) = Id3Text.terminated(data, 1, encoding)
        val key = description.lowercase().filter { it in 'a'..'z' }
        if (key !in NARRATOR_KEYS) return
        into.narrator = Id3Text.decode(data, valueStart, data.size, encoding)
            .split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(SEPARATOR)
    }

    /** `APIC`: encoding, MIME, type, description, data. 2.2's `PIC` has a 3-letter format for the MIME. */
    private fun picture(data: ByteArray, v22: Boolean, into: MetadataBuilder) {
        if (data.size < 4) return
        val encoding = data.u8(0)
        val (format, typeAt) = if (v22) {
            Id3Text.latin1(data, 1, 4) to 4
        } else {
            Id3Text.terminated(data, 1, 0)
        }
        if (typeAt >= data.size) return
        val type = data.u8(typeAt)
        val (_, imageAt) = Id3Text.terminated(data, typeAt + 1, encoding)
        if (format.trim() == "-->" || imageAt >= data.size) return
        val image = data.copyOfRange(imageAt, data.size)
        into.offerCover(Cover(image, mimeType(format, image)), isFront = type == FRONT_COVER)
    }

    private fun mimeType(format: String, image: ByteArray): String {
        when (val mime = format.trim().lowercase()) {
            "image/jpeg", "image/jpg", "jpg", "jpeg" -> return "image/jpeg"
            "png" -> return "image/png"
            "gif" -> return "image/gif"
            "bmp" -> return "image/bmp"
            "" -> {}
            else -> if ('/' in mime) return mime
        }
        return sniff(image)
    }

    private fun sniff(image: ByteArray): String = when {
        image.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        image.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) -> "image/png"
        image.startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "image/gif"
        image.startsWith('B'.code, 'M'.code) -> "image/bmp"
        image.size >= 12 && image.startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
            Id3Text.latin1(image, 8, 12) == "WEBP" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { u8(it) == bytes[it] }

    /** `CHAP`: element ID, start and end in ms, start and end byte offsets, then sub-frames. */
    private fun chapter(data: ByteArray, major: Int): Id3Chapters.Chap? {
        val (id, timesAt) = Id3Text.terminated(data, 0, 0)
        if (timesAt + 16 > data.size) return null
        val sub = subFrames(data, timesAt + 16, major)
        return Id3Chapters.Chap(id, title(sub), data.u32(timesAt), data.u32(timesAt + 4))
    }

    /** `CTOC`: element ID, flags, entry count, entry IDs, then sub-frames. */
    private fun tableOfContents(data: ByteArray, major: Int): Id3Chapters.Toc? {
        val (id, flagsAt) = Id3Text.terminated(data, 0, 0)
        if (flagsAt + 2 > data.size) return null
        val flags = data.u8(flagsAt)
        val count = data.u8(flagsAt + 1)
        var p = flagsAt + 2
        val children = mutableListOf<String>()
        repeat(count) {
            if (p >= data.size) return@repeat
            val (child, next) = Id3Text.terminated(data, p, 0)
            children += child
            p = next
        }
        val sub = subFrames(data, p, major)
        return Id3Chapters.Toc(id, title(sub), topLevel = flags and 0x02 != 0, ordered = flags and 0x01 != 0, children)
    }

    private fun subFrames(data: ByteArray, start: Int, major: Int): List<Id3Frame> {
        val frames = mutableListOf<Id3Frame>()
        if (start < data.size) Id3v2.frames(data, start, data.size, major, tagUnsynchronised = false, frames)
        return frames
    }

    private fun title(frames: List<Id3Frame>): String? =
        frames.firstOrNull { it.id == "TIT2" }?.let(::text)?.trim()?.takeIf { it.isNotEmpty() }

    private const val SEPARATOR = "; "
    private const val FRONT_COVER = 3
    private val NARRATOR_KEYS = setOf("narrator", "narratedby")
    private val YEAR = Regex("""\d{4}""")

    /** By preference: recording time (2.4), year (2.3), release time, original release. */
    private val YEAR_FRAMES = listOf("TDRC", "TYER", "TDRL", "TDOR", "TORY")
}
