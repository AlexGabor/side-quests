package com.alexgabor.lib.audiometadata.mp3

import com.alexgabor.lib.audiometadata.u8

/** The 4-byte header that starts every MPEG audio frame (Layer I, II or III). */
internal class MpegFrameHeader(
    /** 1 for MPEG-1, 2 for MPEG-2, 25 for MPEG-2.5. */
    val version: Int,
    val layer: Int,
    /** In kbit/s; 0 for free format. */
    val bitrate: Int,
    val sampleRate: Int,
    val padding: Boolean,
    val mono: Boolean,
) {
    val samplesPerFrame: Int
        get() = when (layer) {
            1 -> 384
            2 -> 1152
            else -> if (version == 1) 1152 else 576
        }

    /** Whole frame in bytes, header included; `null` for free format. */
    val frameLength: Int?
        get() {
            if (bitrate == 0) return null
            val pad = if (padding) 1 else 0
            return when (layer) {
                1 -> (12 * bitrate * 1000 / sampleRate + pad) * 4
                2 -> 144 * bitrate * 1000 / sampleRate + pad
                else -> (if (version == 1) 144 else 72) * bitrate * 1000 / sampleRate + pad
            }
        }

    /** Where a Xing or Info header starts, counted from the frame start. */
    val xingOffset: Int
        get() = 4 + when {
            version == 1 -> if (mono) 17 else 32
            else -> if (mono) 9 else 17
        }

    fun sameStream(other: MpegFrameHeader) =
        version == other.version && layer == other.layer && sampleRate == other.sampleRate

    companion object {
        /** The header at [offset] in [bytes], or `null` when there is none or it uses a reserved value. */
        fun parse(bytes: ByteArray, offset: Int): MpegFrameHeader? {
            if (offset < 0 || offset + 4 > bytes.size) return null
            val b1 = bytes.u8(offset + 1)
            val b2 = bytes.u8(offset + 2)
            val b3 = bytes.u8(offset + 3)
            if (bytes.u8(offset) != 0xFF || b1 and 0xE0 != 0xE0) return null
            val version = when (b1 shr 3 and 0x3) {
                0 -> 25
                2 -> 2
                3 -> 1
                else -> return null
            }
            val layer = when (b1 shr 1 and 0x3) {
                1 -> 3
                2 -> 2
                3 -> 1
                else -> return null
            }
            val bitrateIndex = b2 shr 4
            if (bitrateIndex == 0xF) return null
            val sampleRateIndex = b2 shr 2 and 0x3
            if (sampleRateIndex == 3) return null
            val bitrate = bitrateTable(version, layer)[bitrateIndex]
            val sampleRate = SAMPLE_RATES[sampleRateIndex] / when (version) {
                1 -> 1
                2 -> 2
                else -> 4
            }
            return MpegFrameHeader(
                version = version,
                layer = layer,
                bitrate = bitrate,
                sampleRate = sampleRate,
                padding = b2 shr 1 and 0x1 == 1,
                mono = b3 shr 6 == 3,
            )
        }

        private fun bitrateTable(version: Int, layer: Int): IntArray = when {
            version == 1 && layer == 1 -> V1_L1
            version == 1 && layer == 2 -> V1_L2
            version == 1 -> V1_L3
            layer == 1 -> V2_L1
            else -> V2_L2_L3
        }

        private val SAMPLE_RATES = intArrayOf(44100, 48000, 32000)
        private val V1_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
        private val V1_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
        private val V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        private val V2_L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
        private val V2_L2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    }
}
