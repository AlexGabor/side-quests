package com.alexgabor.lib.audiometadata.mp3

import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u32
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Duration from the first MPEG frame: its Xing/Info or VBRI header, or else a CBR estimate. */
internal object Mp3Duration {

    class FirstFrame(val position: Long, val header: MpegFrameHeader)

    /** [audioEnd] is where the audio stops: the file size less any trailing ID3v1 tag. */
    fun of(reader: Reader, audioStart: Long, audioEnd: Long): Duration? {
        val first = firstFrame(reader, audioStart, audioEnd) ?: return null
        val header = first.header
        val frame = reader.bytes(first.position, FRAME_PROBE)
        val frames = xingFrames(frame, header) ?: vbriFrames(frame)
        if (frames != null && frames > 0) {
            return (frames.toDouble() * header.samplesPerFrame / header.sampleRate).seconds
        }
        if (header.bitrate == 0) return null
        val audioBytes = audioEnd - first.position
        if (audioBytes <= 0) return null
        return (audioBytes * 8.0 / (header.bitrate * 1000)).seconds
    }

    /**
     * The first frame at or after [start], looking at most [SCAN_LIMIT] bytes ahead. Anything but a
     * frame right at [start] must be followed by another frame of the same stream, so a stray `FF Ex`
     * in padding or junk isn't taken for audio.
     */
    fun firstFrame(reader: Reader, start: Long, end: Long): FirstFrame? {
        var chunkStart = start
        val limit = minOf(end, start + SCAN_LIMIT)
        while (chunkStart < limit) {
            // Overlap by 3 so a header straddling two chunks is still seen.
            val chunk = reader.bytes(chunkStart, minOf(CHUNK.toLong(), limit - chunkStart + 3).toInt())
            if (chunk.size < 4) return null
            for (i in 0..chunk.size - 4) {
                val header = MpegFrameHeader.parse(chunk, i) ?: continue
                val position = chunkStart + i
                if (position == start || confirmed(reader, position, header)) return FirstFrame(position, header)
            }
            chunkStart += chunk.size - 3
        }
        return null
    }

    private fun confirmed(reader: Reader, position: Long, header: MpegFrameHeader): Boolean {
        val length = header.frameLength ?: return false
        val next = MpegFrameHeader.parse(reader.bytes(position + length, 4), 0) ?: return false
        return header.sameStream(next)
    }

    /** Frame count from a Xing (VBR) or Info (CBR) header, when it has one. */
    private fun xingFrames(frame: ByteArray, header: MpegFrameHeader): Long? {
        // Some encoders count the CRC that may follow the header, others don't.
        for (offset in listOf(header.xingOffset, header.xingOffset + 2)) {
            if (frame.startsWith("Xing", offset) || frame.startsWith("Info", offset)) {
                if (offset + 12 > frame.size) return null
                val flags = frame.u32(offset + 4)
                return if (flags and 0x1 != 0L) frame.u32(offset + 8) else null
            }
        }
        return null
    }

    /** Frame count from a Fraunhofer VBRI header, always 32 bytes after the frame header. */
    private fun vbriFrames(frame: ByteArray): Long? {
        if (!frame.startsWith("VBRI", VBRI_OFFSET) || VBRI_OFFSET + 18 > frame.size) return null
        return frame.u32(VBRI_OFFSET + 14)
    }

    private const val FRAME_PROBE = 192
    private const val VBRI_OFFSET = 4 + 32
    private const val CHUNK = 4096
    private const val SCAN_LIMIT = 64L * 1024
}
