package com.alexgabor.lib.audiometadata

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalformedInputTest {

    private val fullTag = Id3Builder(3)
        .text("TIT2", "Title").text("TALB", "Album")
        .frame("APIC", apicBody("image/jpeg", 3, JPEG))
        .ctoc("toc", listOf("c1", "c2"), topLevel = true)
        .chap("c1", 0, 1_000, "One").chap("c2", 1_000, 2_000, "Two")
        .text("TPE1", "Artist")
        .build()

    @Test
    fun truncatedTagKeepsTheFramesBeforeTheCut() {
        val tag = Id3Builder(4).text("TIT2", "Title").text("TALB", "Album").text("TPE1", "Artist").build(declaredSize = 1_000_000)
        val cut = tag.copyOf(tag.size - 3)
        val result = readMp3(cut)
        assertEquals("Title", result.tags.title)
        assertEquals("Album", result.tags.album)
        assertNull(result.tags.artist)
    }

    @Test
    fun frameOverrunningTheTagStopsThatTag() {
        val tag = Id3Builder(3)
            .text("TIT2", "Title")
            .raw(latin1("TALB") + u32(10_000) + bytesOf(0, 0) + textBody("Album", 0))
            .text("TPE1", "Artist")
            .build()
        val result = readMp3(tag + Mpeg.frames(5))
        assertEquals("Title", result.tags.title)
        assertNull(result.tags.album)
        assertNull(result.tags.artist)
        // The audio after the tag is still found.
        assertNotNull(result.duration)
    }

    @Test
    fun invalidFrameIdStopsThatTag() {
        val tag = Id3Builder(3).text("TIT2", "Title").raw(latin1("ti?2") + u32(2) + bytesOf(0, 0, 0, 0x41)).text("TPE1", "A").build()
        assertEquals("Title", readMp3(tag).tags.title)
        assertNull(readMp3(tag).tags.artist)
    }

    @Test
    fun brokenFrameBodiesAreSkippedOneByOne() {
        val tag = Id3Builder(3)
            .frame("APIC", bytesOf(0))
            .frame("CHAP", latin1("c1"))
            .frame("CTOC", latin1("t") + bytesOf(0, 3, 200))
            .frame("TIT2", ByteArray(0))
            .frame("TXXX", bytesOf(1, 0xFF))
            .frame("TRCK", bytesOf(9) + latin1("4"))
            .text("TALB", "Album")
            .build()
        val result = readMp3(tag + Mpeg.frames(2))
        assertEquals("Album", result.tags.album)
        assertEquals(4, result.tags.trackNumber)
        assertNull(result.cover)
    }

    @Test
    fun hugeDeclaredSizesDoNotAllocate() {
        // Maximum syncsafe tag size, with a frame claiming 4 GB.
        val tag = latin1("ID3") + bytesOf(3, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F) + latin1("APIC") + u32(0xFFFFFFF0) + bytesOf(0, 0)
        assertEquals(AudioMetadata(AudioFormat.Mp3), readMp3(tag))
        val ext = latin1("ID3") + bytesOf(3, 0, 0x40) + syncsafe(20) + u32(0x7FFFFFFF) + ByteArray(16)
        assertEquals(AudioMetadata(AudioFormat.Mp3), readMp3(ext))
    }

    @Test
    fun failingSourceAfterDetection() {
        val bytes = fullTag + Mpeg.frames(5)
        val flaky = object : ByteSource {
            var reads = 0
            override val size = bytes.size.toLong()
            override fun read(position: Long, length: Int): ByteArray {
                if (++reads > 2) throw IllegalStateException("gone")
                return ByteArraySource(bytes).read(position, length)
            }
        }
        val result = AudioMetadataReader.read(flaky)
        assertEquals(AudioFormat.Mp3, result?.format)
    }

    @Test
    fun everyTruncationOfAValidFile() {
        val file = fullTag + Mpeg.xing(10) + Mpeg.frames(3) + id3v1("v1")
        for (n in 0..file.size) {
            val result = read(file.copyOf(n))
            if (n >= 10) assertEquals(AudioFormat.Mp3, result?.format, "cut at $n")
        }
        assertEquals("Two", readMp3(file).chapters[1].title)
    }

    @Test
    fun randomCorruptionNeverThrows() {
        val file = fullTag + Mpeg.xing(10) + Mpeg.frames(3) + id3v1("v1")
        val random = Random(42)
        repeat(3000) {
            val copy = file.copyOf()
            repeat(1 + random.nextInt(8)) { copy[random.nextInt(copy.size)] = random.nextInt(256).toByte() }
            read(copy)
        }
    }

    @Test
    fun randomBytesNeverThrow() {
        val random = Random(7)
        repeat(2000) {
            val prefix = when (it % 3) {
                0 -> latin1("ID3") + bytesOf(2 + it % 3, 0, random.nextInt(256))
                1 -> bytesOf(0xFF, 0xFB)
                else -> ByteArray(0)
            }
            read(prefix + random.nextBytes(random.nextInt(600)))
        }
    }

    @Test
    fun largeFileIsReadSparingly() {
        val tag = Id3Builder(3).text("TIT2", "T").frame("APIC", apicBody("image/png", 3, ByteArray(800) { 1 })).build()
        val source = CountingSource(tag + Mpeg.frames(4), virtualSize = 100L * 1024 * 1024)
        val result = AudioMetadataReader.read(source)
        assertEquals("T", result?.tags?.title)
        assertNotNull(result?.duration)
        assertTrue(source.bytesRead < 64 * 1024, "read ${source.bytesRead} bytes")
    }
}
