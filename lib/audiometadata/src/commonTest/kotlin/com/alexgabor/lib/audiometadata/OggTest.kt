package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OggTest {

    private val comments = vorbisComments("TITLE=T", "ARTIST=A")

    @Test
    fun vorbisDuration() {
        val result = readAs(AudioFormat.Vorbis, Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(comments), 24_000))
        assertEquals(3.seconds, result.duration)
        assertEquals("T", result.tags.title)
    }

    @Test
    fun opusDuration() {
        val result = readAs(AudioFormat.Opus, Ogg.stream(Ogg.opusHead(312), Ogg.opusTags(comments), 144_312))
        assertEquals(3.seconds, result.duration)
        assertEquals("A", result.tags.artist)
    }

    @Test
    fun opusIgnoresTheInputRate() {
        // OpusHead says 48 kHz input here, but even an 8 kHz input counts granules at 48 kHz.
        val head = Ogg.opusHead(0).also { le32(8000).copyInto(it, 12) }
        assertEquals(1.seconds, readAs(AudioFormat.Opus, Ogg.stream(head, Ogg.opusTags(comments), 48_000)).duration)
    }

    @Test
    fun commentPacketSpanningPages() {
        val image = PNG + ByteArray(100 * 1024) { (it % 251).toByte() }
        val packet = Ogg.opusTags(vorbisComments("TITLE=Big", "METADATA_BLOCK_PICTURE=" + base64(flacPicture(3, "image/png", image))))
        val file = Ogg.stream(Ogg.opusHead(312), packet, 48_312)
        // 137 KB of base64 needs at least three 64 KB pages.
        assertTrue(file.size > 2 * 65_025)
        val result = readAs(AudioFormat.Opus, file)
        assertEquals("Big", result.tags.title)
        assertEquals(Cover(image, "image/png"), result.cover)
        assertEquals(1.seconds, result.duration)
    }

    @Test
    fun commentPacketOverManySmallPages() {
        val file = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(vorbisComments("TITLE=" + "x".repeat(2000))), 8000, segmentsPerPage = 1)
        assertEquals("x".repeat(2000), readAs(AudioFormat.Vorbis, file).tags.title)
    }

    @Test
    fun fakePageInAudio() {
        // The last page's body holds `OggS` bytes that don't make a page with a valid CRC.
        val fake = latin1("OggS") + bytesOf(0, 4) + le64(999_999) + le32(1) + ByteArray(13)
        val audio = ByteArray(200) + fake + ByteArray(200)
        val file = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(comments), 16_000, audio = listOf(audio))
        assertEquals(2.seconds, readAs(AudioFormat.Vorbis, file).duration)
    }

    @Test
    fun badCrcOnLastPageFallsBackToTheOneBefore() {
        val head = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(comments), 8000)
        val broken = Ogg.page(ByteArray(40), sequence = 9, granule = 80_000, flags = 0x04, badCrc = true)
        assertEquals(1.seconds, readAs(AudioFormat.Vorbis, head + broken).duration)
    }

    @Test
    fun multiplexedStream() {
        val vorbis = 1L
        val other = 77L
        val file = concat(
            listOf(
                Ogg.page(Ogg.vorbisId(8000), vorbis, 0, 0, flags = 0x02),
                Ogg.page(ByteArray(30), other, 0, 0, flags = 0x02),
                Ogg.page(Ogg.vorbisComments(vorbisComments("TITLE=Mine")), other, 1, 0),
            ) + Ogg.pages(Ogg.vorbisComments(vorbisComments("TITLE=Vorbis")), vorbis, 1) + listOf(
                Ogg.page(ByteArray(30), vorbis, 2, 40_000, flags = 0x04),
                Ogg.page(ByteArray(30), other, 2, 999_999, flags = 0x04),
            ),
        )
        val result = readAs(AudioFormat.Vorbis, file)
        assertEquals("Vorbis", result.tags.title)
        assertEquals(5.seconds, result.duration)
    }

    @Test
    fun lastPageBeyondTheFirstWindow() {
        // A 40 KB last page: no whole page fits in the last 16 KB, so the 64 KB window finds it.
        val big = Ogg.page(ByteArray(40_000), sequence = 3, granule = 32_000, flags = 0x04, lacing = IntArray(157) { if (it < 156) 255 else 40_000 - 156 * 255 })
        val file = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(comments), 8000) + big
        assertEquals(4.seconds, readAs(AudioFormat.Vorbis, file).duration)
    }

    @Test
    fun noGranuleNoDuration() {
        val file = concat(listOf(Ogg.page(Ogg.opusHead(312), flags = 0x02)) + Ogg.pages(Ogg.opusTags(comments), granule = -1))
        val result = readAs(AudioFormat.Opus, file)
        assertNull(result.duration)
        assertEquals("T", result.tags.title)
    }

    @Test
    fun largeFileReadsLittle() {
        val head = concat(listOf(Ogg.page(Ogg.opusHead(312), flags = 0x02)) + Ogg.pages(Ogg.opusTags(vorbisComments("TITLE=T", "X=" + "y".repeat(2000)))))
        val size = 100L * 1024 * 1024
        val pages = concat(List(4) { Ogg.page(ByteArray(4000), sequence = 10L + it, granule = 48_000L * 3600 + 312 + it) })
        val source = SparseSource(size, listOf(0L to head, size - pages.size to pages))
        val result = AudioMetadataReader.read(source)!!
        assertEquals("T", result.tags.title)
        assertEquals((3600).seconds + (3.0 / 48_000).seconds, result.duration)
        assertTrue(source.bytesRead < 64 * 1024, "read ${source.bytesRead} bytes")
    }
}
