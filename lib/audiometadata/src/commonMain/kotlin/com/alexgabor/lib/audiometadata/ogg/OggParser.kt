package com.alexgabor.lib.audiometadata.ogg

import com.alexgabor.lib.audiometadata.AudioFormat
import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.durationOf
import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.le16
import com.alexgabor.lib.audiometadata.le32
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u8
import com.alexgabor.lib.audiometadata.vorbis.Vorbis
import com.alexgabor.lib.audiometadata.vorbis.VorbisComments

/**
 * Ogg Vorbis and Opus: the identification and comment headers from the first pages, and the duration
 * from the last page's granule position. The pages in between are never read.
 */
internal object OggParser {

    /** The codec of an Ogg stream whose first page starts [probe], or `null` for other codecs. */
    fun detect(probe: ByteArray): AudioFormat? {
        val page = OggPage.parse(probe, 0)?.takeIf { it.isFirst } ?: return null
        val packet = page.headerSize
        return when {
            probe.u8OrNull(packet) == 1 && probe.startsWith("vorbis", packet + 1) -> AudioFormat.Vorbis
            probe.startsWith("OpusHead", packet) -> AudioFormat.Opus
            else -> null
        }
    }

    val vorbisParser: FormatParser = FormatParser { reader, into -> parse(reader, into, opus = false) }
    val opusParser: FormatParser = FormatParser { reader, into -> parse(reader, into, opus = true) }

    private fun parse(reader: Reader, into: MetadataBuilder, opus: Boolean) {
        val start = guarded { Id3v2.skipTags(reader).end } ?: 0L
        val (first, packets) = OggPages.packets(reader, start, count = 2, maxPacket = MAX_COMMENT) ?: return
        val id = packets.getOrNull(0) ?: return
        val comments = packets.getOrNull(1)?.let { packet ->
            guarded {
                when {
                    opus && packet.startsWith("OpusTags") -> VorbisComments.parse(packet, 8)
                    !opus && packet.size > 7 && packet[0].toInt() == 3 && packet.startsWith("vorbis", 1) -> VorbisComments.parse(packet, 7)
                    else -> null
                }
            }
        }
        if (comments != null) {
            guarded { Vorbis.applyTags(comments, into) }
            guarded { Vorbis.applyCovers(comments, into) }
        }
        guarded {
            val granule = OggPages.lastGranule(reader, start, first.serial)
            into.duration = when {
                granule == null -> null
                opus -> if (id.size >= 12 && id.startsWith("OpusHead")) durationOf(granule - id.le16(10), OPUS_RATE) else null
                else -> if (id.size >= 16) durationOf(granule, id.le32(12)) else null
            }
        }
        if (comments != null) guarded { into.chapters = Vorbis.chapters(comments, into.duration) }
    }

    private fun ByteArray.u8OrNull(i: Int): Int? = if (i < size) u8(i) else null

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    /** Opus granule positions always count 48 kHz samples, whatever the input rate was. */
    private const val OPUS_RATE = 48_000L

    /** Covers make comment headers big, but not this big. */
    private const val MAX_COMMENT = 32 * 1024 * 1024
}
