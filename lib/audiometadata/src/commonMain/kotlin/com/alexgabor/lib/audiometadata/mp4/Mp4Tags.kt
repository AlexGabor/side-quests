package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.Cover
import com.alexgabor.lib.audiometadata.Images
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.ascii
import com.alexgabor.lib.audiometadata.id3.Genres
import com.alexgabor.lib.audiometadata.id3.Id3Text
import com.alexgabor.lib.audiometadata.u16
import com.alexgabor.lib.audiometadata.u24

/** iTunes-style tags: the items of `moov/udta/meta/ilst`. */
internal object Mp4Tags {

    /** Reads `meta/ilst` from the children of `moov/udta`. Each item is decoded on its own. */
    fun read(reader: Reader, udta: List<Atom>, into: MetadataBuilder) {
        val meta = udta.container("meta") ?: return
        // ISO `meta` is a full box; QuickTime's has no version and flags, so its first child comes at once.
        val peek = reader.bytes(meta.payload, 8)
        val first = if (peek.size == 8 && peek.ascii(4, 4) == "hdlr") meta.payload else meta.payload + 4
        val ilst = Atoms.children(reader, first, meta.end).container("ilst") ?: return
        val bytes = Atoms.payload(reader, ilst, MAX_ILST)
        val items = Atoms.inMemory(bytes)
        val found = Found()
        for (item in Atoms.children(items, 0, bytes.size.toLong())) {
            // An item cut off by the end of `ilst` or of the file is dropped; the walk ends there anyway.
            if (item.truncated) break
            try {
                applyItem(items, item, into, found)
            } catch (_: Exception) {
                // Skip just this item.
            }
        }
        into.narrator = found.narrator ?: found.freeformNarrator
        into.genre = found.genre ?: found.genreIndex?.let { Genres.name(it - 1) }
    }

    /** Values ranked against each other, applied once every item has been seen. */
    private class Found {
        var narrator: String? = null
        var freeformNarrator: String? = null
        var genre: String? = null
        var genreIndex: Int? = null
    }

    private fun applyItem(items: Reader, item: Atom, into: MetadataBuilder, found: Found) {
        val children = Atoms.children(items, item)
        if (item.type == FREEFORM) return freeform(items, children, found)
        val data = children.leaf("data")?.let { Atoms.payload(items, it, Int.MAX_VALUE) } ?: return
        // Version (1 byte), type (3 bytes), locale (4 bytes), then the value.
        if (data.size < 8) return
        val type = data.u24(1)
        val value = data.copyOfRange(8, data.size)
        when (item.type) {
            "©nam" -> into.title = text(type, value)
            "©alb" -> into.album = text(type, value)
            "©ART" -> into.artist = text(type, value)
            "aART" -> into.albumArtist = text(type, value)
            "©wrt" -> into.composer = text(type, value)
            "©nrt" -> if (found.narrator == null) found.narrator = text(type, value)
            "©gen" -> if (found.genre == null) found.genre = text(type, value)
            "gnre" -> if (found.genreIndex == null && value.size >= 2) found.genreIndex = value.u16(0)
            "©day" -> into.year = text(type, value)?.let { YEAR.find(it) }?.value?.toIntOrNull()
            "trkn" -> if (value.size >= 6) { into.trackNumber = value.u16(2); into.trackTotal = value.u16(4) }
            "disk" -> if (value.size >= 6) { into.discNumber = value.u16(2); into.discTotal = value.u16(4) }
            "covr" -> into.offerCover(Cover(value, mimeType(type, value)), isFront = true)
        }
    }

    /** `----`: `mean` (a reverse-DNS owner), `name`, then `data`. Only the narrator is read. */
    private fun freeform(items: Reader, children: List<Atom>, found: Found) {
        if (found.freeformNarrator != null) return
        val name = children.leaf("name")?.let { Atoms.payload(items, it, MAX_NAME) } ?: return
        if (name.size <= 4) return
        val key = name.decodeToString(4, name.size).lowercase().filter { it in 'a'..'z' }
        if (key !in NARRATOR_KEYS) return
        val data = children.leaf("data")?.let { Atoms.payload(items, it, Int.MAX_VALUE) } ?: return
        if (data.size < 8) return
        found.freeformNarrator = text(data.u24(1), data.copyOfRange(8, data.size))
    }

    /** Type 2 is UTF-16BE; everything else that is read as text is UTF-8. */
    private fun text(type: Int, value: ByteArray): String? {
        val s = Id3Text.decode(value, 0, value.size, if (type == UTF16) 2 else 3)
        return s.trim('\u0000', ' ', '\t', '\n', '\r').takeIf { it.isNotEmpty() }
    }

    private fun mimeType(type: Int, image: ByteArray): String = when (type) {
        JPEG -> "image/jpeg"
        PNG -> "image/png"
        BMP -> "image/bmp"
        else -> Images.sniffMimeType(image)
    }

    private const val FREEFORM = "----"
    private const val UTF16 = 2
    private const val JPEG = 13
    private const val PNG = 14
    private const val BMP = 27
    private const val MAX_ILST = 32 * 1024 * 1024
    private const val MAX_NAME = 256
    private val NARRATOR_KEYS = setOf("narrator", "narratedby")
    private val YEAR = Regex("""\d{4}""")
}
