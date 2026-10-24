package com.alexgabor.lib.audiometadata.wav

import com.alexgabor.lib.audiometadata.ByteArraySource
import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.Text
import com.alexgabor.lib.audiometadata.ascii
import com.alexgabor.lib.audiometadata.durationOf
import com.alexgabor.lib.audiometadata.id3.Id3Chapters
import com.alexgabor.lib.audiometadata.id3.Id3Frames
import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.le32
import com.alexgabor.lib.audiometadata.startsWith

/**
 * WAV: walks the RIFF chunks by header. `fmt ` and `data` give the duration, `LIST/INFO` the tags, and
 * an `id3 ` chunk an ID3v2 tag that wins over `INFO`. The `data` payload is never read.
 */
internal object WavParser : FormatParser {

    /** `RIFF`, then `WAVE` at offset 8. */
    fun isWav(probe: ByteArray): Boolean = probe.startsWith("RIFF") && probe.startsWith("WAVE", 8)

    override fun parse(reader: Reader, into: MetadataBuilder) {
        var byteRate: Long? = null
        var dataSize: Long? = null
        val infos = mutableListOf<ByteArray>()
        val id3Tags = mutableListOf<ByteArray>()
        guarded {
            var p = 12L
            var count = 0
            while (count++ < MAX_CHUNKS) {
                val header = reader.bytes(p, 8)
                if (header.size < 8) break
                val id = header.ascii(0, 4)
                val size = header.le32(4)
                val payload = p + 8
                val left = reader.size - payload
                if (id == "data") {
                    // Streaming writers leave the size at 0xFFFFFFFF, or at what they hoped to write.
                    val actual = if (size == 0xFFFFFFFFL || size > left) left else size
                    if (dataSize == null) dataSize = actual
                    if (actual != size) break
                } else {
                    if (size > left) break
                    when (id) {
                        "fmt " -> if (byteRate == null) reader.bytes(payload, 16).takeIf { it.size >= 12 }?.let { byteRate = it.le32(8) }
                        "LIST" -> if (size >= 4 && reader.bytes(payload, 4).startsWith("INFO")) infos += reader.bytes(payload + 4, minOf(size - 4, MAX_INFO).toInt())
                        "id3 ", "ID3 " -> id3Tags += reader.bytes(payload, minOf(size, MAX_ID3).toInt())
                    }
                }
                p = payload + size + (size and 1)
            }
        }
        val chapters = Id3Chapters()
        for (tag in id3Tags) {
            guarded { Id3v2.readTags(Reader(ByteArraySource(tag))) { major, frames -> Id3Frames.apply(frames, major, into, chapters) } }
        }
        for (info in infos) guarded { info(info, into) }
        guarded { into.duration = durationOf(dataSize, byteRate) }
        guarded { into.chapters = chapters.build(into.duration) }
    }

    /** `INFO` sub-chunks: ID, size, NUL-terminated text padded to an even length. */
    private fun info(b: ByteArray, into: MetadataBuilder) {
        var p = 0
        while (p + 8 <= b.size) {
            val id = b.ascii(p, 4)
            val size = b.le32(p + 4)
            if (size > b.size - p - 8) break
            val start = p + 8
            var end = start + size.toInt()
            while (end > start && b[end - 1].toInt() == 0) end--
            val value = Text.utf8OrWindows1252(b, start, end).substringBefore('\u0000')
            when (id) {
                "INAM" -> into.title = value
                "IPRD" -> into.album = value
                "IART" -> into.artist = value
                "IGNR" -> into.genre = value
                "ICRD" -> into.year = YEAR.find(value)?.value?.toIntOrNull()
                "IPRT", "ITRK" -> {
                    val parts = value.split('/')
                    into.trackNumber = parts[0].trim().toIntOrNull()
                    into.trackTotal = parts.getOrNull(1)?.trim()?.toIntOrNull()
                }
            }
            p = start + size.toInt() + (size.toInt() and 1)
        }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private val YEAR = Regex("""\d{4}""")
    private const val MAX_CHUNKS = 4096
    private const val MAX_INFO = 1L * 1024 * 1024
    private const val MAX_ID3 = 64L * 1024 * 1024
}
