package com.alexgabor.lib.audiometadata.flac

import com.alexgabor.lib.audiometadata.Chapter
import com.alexgabor.lib.audiometadata.CueSheet
import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.durationOf
import com.alexgabor.lib.audiometadata.id3.Id3Chapters
import com.alexgabor.lib.audiometadata.id3.Id3Frames
import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u24
import com.alexgabor.lib.audiometadata.u32
import com.alexgabor.lib.audiometadata.u8
import com.alexgabor.lib.audiometadata.vorbis.FlacPicture
import com.alexgabor.lib.audiometadata.vorbis.Vorbis
import com.alexgabor.lib.audiometadata.vorbis.VorbisComments

/**
 * FLAC: the metadata blocks after `fLaC` (STREAMINFO, VORBIS_COMMENT, PICTURE, CUESHEET), and a leading
 * ID3v2 tag to fill gaps. Audio frames are never read. Each block is decoded in its own guard.
 */
internal object FlacParser : FormatParser {

    override fun parse(reader: Reader, into: MetadataBuilder) {
        val start = guarded { Id3v2.skipTags(reader).end } ?: 0L
        if (!reader.bytes(start, 4).startsWith("fLaC")) return
        var sampleRate = 0L
        var comments: VorbisComments? = null
        var cueSheet: ByteArray? = null
        for (block in blocks(reader, start + 4)) {
            guarded {
                when (block.type) {
                    STREAMINFO -> {
                        val b = reader.bytes(block.payload, block.length)
                        if (b.size >= 18) {
                            sampleRate = (b.u8(10).toLong() shl 12) or (b.u8(11).toLong() shl 4) or (b.u8(12).toLong() shr 4)
                            val samples = ((b.u8(13).toLong() and 0x0F) shl 32) or b.u32(14)
                            into.duration = durationOf(samples, sampleRate)
                        }
                    }
                    VORBIS_COMMENT -> {
                        val parsed = VorbisComments.parse(reader.bytes(block.payload, block.length))
                        if (comments == null) comments = parsed
                        guarded { Vorbis.applyTags(parsed, into) }
                        guarded { Vorbis.applyCovers(parsed, into) }
                    }
                    PICTURE -> FlacPicture.parse(reader.bytes(block.payload, block.length))?.let { into.offerCover(it.cover, it.isFront) }
                    CUESHEET -> if (cueSheet == null) cueSheet = reader.bytes(block.payload, block.length)
                }
            }
        }
        // After the blocks, so the ID3 tag only fills what the FLAC metadata lacks. Its chapters aren't used.
        guarded {
            val hadCover = into.hasCover
            Id3v2.readTags(reader) { major, frames ->
                val kept = if (hadCover) frames.filter { it.id != "APIC" && it.id != "PIC" } else frames
                Id3Frames.apply(kept, major, into, Id3Chapters())
            }
        }
        guarded { into.chapters = chapters(comments, cueSheet, sampleRate, into) }
    }

    /** `CHAPTERnnn` comments, else the CUESHEET block, else a `CUESHEET` comment. */
    private fun chapters(comments: VorbisComments?, cueSheet: ByteArray?, sampleRate: Long, into: MetadataBuilder): List<Chapter> {
        val duration = into.duration
        comments?.let { guarded { Vorbis.chapters(it, duration) } }?.takeIf { it.isNotEmpty() }?.let { return it }
        val embedded = comments?.joined("CUESHEET")?.let { CueSheet.parse(it.encodeToByteArray()) }
        cueSheet?.let { guarded { FlacCueSheet.chapters(it, sampleRate, embedded, duration) } }
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        val file = embedded?.files?.firstOrNull() ?: return emptyList()
        return embedded.chaptersOf(file, duration)
    }

    private class Block(val type: Int, val payload: Long, val length: Int)

    /**
     * Block headers from [from]: last-block flag, type, 24-bit size. Stops after the last block, at one
     * that runs past the source (which is still returned, cut short), or after [MAX_BLOCKS].
     */
    private fun blocks(reader: Reader, from: Long): List<Block> {
        val out = mutableListOf<Block>()
        var p = from
        while (out.size < MAX_BLOCKS) {
            val header = reader.bytes(p, 4)
            if (header.size < 4) break
            val type = header.u8(0) and 0x7F
            if (type == INVALID) break
            val length = header.u24(1)
            out += Block(type, p + 4, length)
            p += 4 + length
            if (header.u8(0) and 0x80 != 0 || p > reader.size) break
        }
        return out
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private const val STREAMINFO = 0
    private const val VORBIS_COMMENT = 4
    private const val CUESHEET = 5
    private const val PICTURE = 6
    private const val INVALID = 127
    private const val MAX_BLOCKS = 1024
}
