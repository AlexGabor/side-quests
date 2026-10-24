package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration

class DurationTest {

    private fun seconds(d: Duration?) = d!!.inWholeMicroseconds / 1e6

    private val frameSeconds = Mpeg.SAMPLES.toDouble() / Mpeg.RATE

    @Test
    fun xingFrameCount() {
        // The header's count wins over what the file size would suggest.
        val d = readMp3(Mpeg.xing(1000) + Mpeg.frames(3)).duration
        assertEquals(1000 * frameSeconds, seconds(d), 1e-6)
    }

    @Test
    fun infoFrameCount() {
        val d = readMp3(Id3Builder(4).text("TIT2", "T").build() + Mpeg.xing(500, tag = "Info") + Mpeg.frames(3)).duration
        assertEquals(500 * frameSeconds, seconds(d), 1e-6)
    }

    @Test
    fun xingAfterCrc() {
        val f = Mpeg.frame(bytesOf(0xFF, 0xFA, 0x90, 0x00))
        (latin1("Xing") + u32(1) + u32(200)).copyInto(f, 38)
        assertEquals(200 * frameSeconds, seconds(readMp3(f + Mpeg.frames(2)).duration), 1e-6)
    }

    @Test
    fun xingWithoutFrameCountFallsBackToCbr() {
        val f = Mpeg.frame()
        (latin1("Xing") + u32(0x2) + u32(999)).copyInto(f, 36)
        val audio = f + Mpeg.frames(9)
        assertEquals(audio.size * 8.0 / 128_000, seconds(readMp3(audio).duration), 1e-6)
    }

    @Test
    fun monoAndMpeg2XingOffsets() {
        // MPEG-1 mono: side info 17 bytes. MPEG-2 Layer III mono at 22.05 kHz, 32 kbit/s: 9 bytes, 576 samples.
        val mono1 = Mpeg.frame(bytesOf(0xFF, 0xFB, 0x90, 0xC0))
        (latin1("Xing") + u32(1) + u32(100)).copyInto(mono1, 21)
        assertEquals(100 * frameSeconds, seconds(readMp3(mono1 + Mpeg.frames(2)).duration), 1e-6)

        val header2 = bytesOf(0xFF, 0xF3, 0x40, 0xC0)
        val mono2 = Mpeg.frame(header2, 104)
        (latin1("Xing") + u32(1) + u32(100)).copyInto(mono2, 13)
        val audio = mono2 + Mpeg.frames(2, header2, 104)
        assertEquals(100 * 576.0 / 22050, seconds(readMp3(audio).duration), 1e-6)
    }

    @Test
    fun vbriFrameCount() {
        val d = readMp3(Mpeg.vbri(750) + Mpeg.frames(3)).duration
        assertEquals(750 * frameSeconds, seconds(d), 1e-6)
    }

    @Test
    fun cbrEstimateExcludesTags() {
        val audio = Mpeg.frames(100)
        val file = Id3Builder(3).text("TIT2", "T").build() + audio + id3v1("T")
        assertEquals(audio.size * 8.0 / 128_000, seconds(readMp3(file).duration), 1e-6)
    }

    @Test
    fun firstFrameIsFoundPastPaddingAndJunk() {
        // Undeclared zero padding and a stray sync-looking byte pair before the real audio.
        val junk = ByteArray(300) + bytesOf(0xFF, 0xFB, 0x90, 0x00) + ByteArray(50)
        val file = Id3Builder(3).text("TIT2", "T").build() + junk + Mpeg.xing(42) + Mpeg.frames(3)
        assertEquals(42 * frameSeconds, seconds(readMp3(file).duration), 1e-6)
    }

    @Test
    fun freeFormatHasNoDuration() {
        assertNull(readMp3(Mpeg.frame(bytesOf(0xFF, 0xFB, 0x00, 0x00), 400)).duration)
    }

    @Test
    fun tagOnlyHasNoDuration() {
        val result = readMp3(Id3Builder(3).text("TIT2", "T").build())
        assertNull(result.duration)
        assertEquals("T", result.tags.title)
    }

    @Test
    fun noTagsAtAll() {
        val result = readMp3(Mpeg.frames(10))
        assertEquals(AudioMetadata(AudioFormat.Mp3, duration = result.duration), result)
        assertEquals(10 * Mpeg.FRAME * 8.0 / 128_000, seconds(result.duration), 1e-6)
    }
}
