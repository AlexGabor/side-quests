package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectionTest {

    private fun detect(bytes: ByteArray) = AudioMetadataReader.detect(ByteArraySource(bytes))

    @Test
    fun byteArraySourceReadsShortAtTheEnd() {
        val source = ByteArraySource(ByteArray(10) { it.toByte() })
        assertContentEquals(bytesOf(6, 7, 8, 9), source.read(6, 8))
        assertEquals(0, source.read(10, 4).size)
        assertEquals(0, source.read(-1, 4).size)
    }

    @Test
    fun mpegFrameWithoutId3IsMp3() {
        assertEquals(AudioFormat.Mp3, detect(Mpeg.frames(2)))
    }

    @Test
    fun eachLayerAndVersionIsMp3() {
        // MPEG-1 Layer I, MPEG-1 Layer II, MPEG-2 Layer III, MPEG-2.5 Layer III.
        for (b1 in listOf(0xFF, 0xFD, 0xF3, 0xE3)) {
            assertEquals(AudioFormat.Mp3, detect(bytesOf(0xFF, b1, 0x50, 0x00) + ByteArray(100)), "b1=$b1")
        }
    }

    @Test
    fun mpegFrameAfterId3IsMp3() {
        assertEquals(AudioFormat.Mp3, detect(Id3Builder(3).text("TIT2", "T").build() + Mpeg.frames(2)))
    }

    @Test
    fun id3FollowedByNothingIsMp3() {
        assertEquals(AudioFormat.Mp3, detect(Id3Builder(4).text("TIT2", "T").build()))
    }

    @Test
    fun unrecognisedBytesAreNull() {
        assertNull(detect(latin1("%PDF-1.7\n") + ByteArray(100)))
        assertNull(detect(ByteArray(0)))
        assertNull(read(latin1("%PDF-1.7\n") + ByteArray(100)))
        assertNull(read(ByteArray(0)))
        assertNull(detect(latin1("ID")))
    }

    @Test
    fun reservedHeaderValuesAreNotMp3() {
        val reserved = listOf(
            bytesOf(0xFF, 0xEB, 0x90, 0x00), // version 01
            bytesOf(0xFF, 0xF9, 0x90, 0x00), // layer 00 (ADTS AAC uses this)
            bytesOf(0xFF, 0xFB, 0xF0, 0x00), // bitrate 1111
            bytesOf(0xFF, 0xFB, 0x9C, 0x00), // sample rate 11
            bytesOf(0xFF, 0x7B, 0x90, 0x00), // incomplete sync
        )
        for (header in reserved) assertNull(detect(header + ByteArray(100)), header.joinToString())
    }

    @Test
    fun failingSourceIsNull() {
        assertNull(AudioMetadataReader.detect(FailingSource))
        assertNull(AudioMetadataReader.read(FailingSource))
    }
}
