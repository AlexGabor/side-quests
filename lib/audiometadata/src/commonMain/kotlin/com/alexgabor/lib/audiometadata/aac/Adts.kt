package com.alexgabor.lib.audiometadata.aac

import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.id3.Id3v1
import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.u8
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** An ADTS frame header: the 7 (or 9, with CRC) bytes before each raw AAC frame. */
internal class AdtsHeader(val sampleRate: Int, val frameLength: Int, val samples: Int) {

    companion object {
        /** The header at [i], or `null` unless sync, layer `00`, sample rate and length all check out. */
        fun parse(b: ByteArray, i: Int): AdtsHeader? {
            if (i < 0 || i + 7 > b.size) return null
            if (b.u8(i) != 0xFF || b.u8(i + 1) and 0xF6 != 0xF0) return null
            val rateIndex = (b.u8(i + 2) shr 2) and 0x0F
            if (rateIndex >= RATES.size) return null
            val headerSize = if (b.u8(i + 1) and 0x01 != 0) 7 else 9
            val length = ((b.u8(i + 3) and 0x03) shl 11) or (b.u8(i + 4) shl 3) or (b.u8(i + 5) shr 5)
            if (length < headerSize) return null
            return AdtsHeader(RATES[rateIndex], length, ((b.u8(i + 6) and 0x03) + 1) * 1024)
        }

        private val RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
    }
}

/**
 * Raw AAC in ADTS frames: duration only, from a bounded scan of frame headers. Past [SCAN_LIMIT] the
 * rest of the file is assumed to have the scanned part's average bitrate.
 */
internal object Adts : FormatParser {

    /** A header at [start], followed by a second one unless the frame reaches the end of the source. */
    fun isAdts(reader: Reader, start: Long): Boolean {
        val header = AdtsHeader.parse(reader.bytes(start, 9), 0) ?: return false
        val next = start + header.frameLength
        return next >= reader.size || AdtsHeader.parse(reader.bytes(next, 9), 0) != null
    }

    override fun parse(reader: Reader, into: MetadataBuilder) {
        val start = try { Id3v2.skipTags(reader).end } catch (_: Exception) { 0L }
        val end = reader.size - if (try { Id3v1.isPresent(reader) } catch (_: Exception) { false }) Id3v1.SIZE else 0
        into.duration = duration(reader, start, end)
    }

    fun duration(reader: Reader, start: Long, end: Long): Duration? {
        var position = start
        var samples = 0L
        var rate = 0
        var chunk = Reader.EMPTY
        var chunkStart = start
        while (position < end && position - start < SCAN_LIMIT) {
            if (position + 9 > chunkStart + chunk.size) {
                chunkStart = position
                chunk = reader.bytes(position, CHUNK)
            }
            val header = AdtsHeader.parse(chunk, (position - chunkStart).toInt()) ?: break
            if (rate == 0) rate = header.sampleRate
            samples += header.samples
            position += header.frameLength
        }
        if (rate == 0 || samples == 0L) return null
        val scanned = nanos(samples, rate)
        if (position >= end) return scanned.nanoseconds
        val scannedBytes = position - start
        val audioBytes = end - start
        return (scanned.toDouble() * audioBytes / scannedBytes).toLong().nanoseconds
    }

    private fun nanos(samples: Long, rate: Int): Long = samples / rate * 1_000_000_000 + samples % rate * 1_000_000_000 / rate

    private const val CHUNK = 64 * 1024
    private const val SCAN_LIMIT = 256L * 1024
}
