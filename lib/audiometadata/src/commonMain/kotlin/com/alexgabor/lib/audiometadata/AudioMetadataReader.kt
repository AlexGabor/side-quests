package com.alexgabor.lib.audiometadata

import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.mp3.MpegFrameHeader
import com.alexgabor.lib.audiometadata.mp3.Mp3Parser
import com.alexgabor.lib.audiometadata.mp4.Mp4Parser
import com.alexgabor.lib.audiometadata.mp4.Mp4Parser.isMp4

/**
 * Reads tags, duration, cover and chapters from an audio file. The format is told from the bytes, never
 * from a file name. Nothing here throws or logs: broken input gives whatever could be read.
 */
object AudioMetadataReader {

    /** The format of [source], or `null` when it isn't one this library reads. */
    fun detect(source: ByteSource): AudioFormat? = guarded { detect(Reader(source)) }

    /**
     * Everything [source] says about itself, or `null` when its format isn't recognised. A recognised
     * but damaged file gives a partial result rather than `null`.
     */
    fun read(source: ByteSource): AudioMetadata? {
        val reader = Reader(source)
        val format = guarded { detect(reader) } ?: return null
        val builder = MetadataBuilder(format)
        try {
            parserFor(format).parse(reader, builder)
        } catch (_: Exception) {
            // Keep what was found before the failure.
        }
        return guarded { builder.build() } ?: AudioMetadata(format)
    }

    private fun detect(reader: Reader): AudioFormat? {
        val id3 = Id3v2.skipTags(reader)
        val probe = reader.bytes(id3.end, PROBE_SIZE)
        return when {
            // Before the MPEG check, so an `ftyp` size can't pass for a frame sync.
            isMp4(probe) -> AudioFormat.Mp4
            MpegFrameHeader.parse(probe, 0) != null -> AudioFormat.Mp3
            // Formats that may also carry a leading ID3 tag are checked before falling back to MP3.
            id3.count > 0 -> AudioFormat.Mp3
            else -> null
        }
    }

    private fun parserFor(format: AudioFormat): FormatParser = when (format) {
        AudioFormat.Mp3 -> Mp3Parser
        AudioFormat.Mp4 -> Mp4Parser
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private const val PROBE_SIZE = 64
}

/** Reads one format into a [MetadataBuilder]. May throw; the caller keeps what was built so far. */
internal fun interface FormatParser {
    fun parse(reader: Reader, into: MetadataBuilder)
}
