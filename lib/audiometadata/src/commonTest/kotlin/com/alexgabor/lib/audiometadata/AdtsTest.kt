package com.alexgabor.lib.audiometadata

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class AdtsTest {

    @Test
    fun shortFileIsExact() {
        val result = readAs(AudioFormat.Aac, AdtsFrames.frames(30, 20))
        assertEquals((30 * 1024 / 8000.0).seconds, result.duration)
        assertEquals(AudioTags(), result.tags)
    }

    @Test
    fun rawBlocksAndCrc() {
        val frames = AdtsFrames.frame(30, blocks = 2, crc = true) + AdtsFrames.frame(30, blocks = 4)
        assertEquals((6 * 1024 / 8000.0).seconds, readAs(AudioFormat.Aac, frames).duration)
    }

    @Test
    fun trailingId3v1IsNotAudio() {
        val result = readAs(AudioFormat.Aac, AdtsFrames.frames(10, 20) + id3v1(title = "Ignored"))
        assertEquals((10 * 1024 / 8000.0).seconds, result.duration)
        assertNull(result.tags.title)
    }

    @Test
    fun leadingId3IsSkipped() {
        val id3 = Id3Builder(4).text("TIT2", "Ignored").build()
        val result = readAs(AudioFormat.Aac, id3 + AdtsFrames.frames(10, 20))
        assertEquals((10 * 1024 / 8000.0).seconds, result.duration)
        // Duration only, as the spec says.
        assertNull(result.tags.title)
    }

    @Test
    fun largeFileIsEstimated() {
        val size = 10L * 1024 * 1024
        val frames = AdtsFrames.frames(1024, 400, AdtsFrames.RATE_44100)
        // The scan only ever sees the first 256 KB; the rest repeats the same frames.
        val parts = (0 until size / frames.size).map { it * frames.size to frames }
        val source = SparseSource(size, parts)
        val result = AudioMetadataReader.read(source)!!
        val expected = size / 400.0 * 1024 / 44_100
        val actual = result.duration!!.toDouble(DurationUnit.SECONDS)
        assertTrue(abs(actual - expected) / expected < 0.01, "expected about $expected s, got $actual s")
        assertTrue(source.bytesRead < 512 * 1024, "read ${source.bytesRead} bytes")
    }

    @Test
    fun lostSyncIsEstimated() {
        // Ten frames, then junk: the scan stops and scales by the remaining bytes.
        val file = AdtsFrames.frames(10, 100) + ByteArray(1000)
        val result = readAs(AudioFormat.Aac, file)
        assertEquals((10 * 1024 / 8000.0 * 2).seconds, result.duration)
    }
}
