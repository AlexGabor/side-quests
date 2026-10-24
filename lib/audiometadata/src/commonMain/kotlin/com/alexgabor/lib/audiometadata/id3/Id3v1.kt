package com.alexgabor.lib.audiometadata.id3

import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u8

/** The 128-byte `TAG` block at the end of the file, versions 1 and 1.1. */
internal object Id3v1 {
    const val SIZE = 128

    /** Whether the file ends in an ID3v1 tag. */
    fun isPresent(reader: Reader): Boolean =
        reader.size >= SIZE && reader.bytes(reader.size - SIZE, 3).startsWith("TAG")

    /** Fills only the fields still missing in [into]. */
    fun read(reader: Reader, into: MetadataBuilder) {
        if (reader.size < SIZE) return
        val tag = reader.bytes(reader.size - SIZE, SIZE)
        if (tag.size < SIZE || !tag.startsWith("TAG")) return
        into.title = field(tag, 3, 30)
        into.artist = field(tag, 33, 30)
        into.album = field(tag, 63, 30)
        into.year = field(tag, 93, 4)?.toIntOrNull()
        // 1.1 steals the comment's last two bytes for a zero and the track number.
        if (tag[125].toInt() == 0 && tag[126].toInt() != 0) into.trackNumber = tag.u8(126)
        into.genre = Genres.name(tag.u8(127))
    }

    private fun field(tag: ByteArray, start: Int, length: Int): String? {
        var end = start
        while (end < start + length && tag[end].toInt() != 0) end++
        return text(tag, start, end).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Latin-1 by the spec, but some writers (ffmpeg among them) put UTF-8 there. Text that decodes as
     * UTF-8 is taken as UTF-8: Latin-1 accented letters on their own almost never form valid sequences.
     */
    private fun text(tag: ByteArray, start: Int, end: Int): String = try {
        tag.decodeToString(start, end, throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        Id3Text.latin1(tag, start, end)
    }
}
