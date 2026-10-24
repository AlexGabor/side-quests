package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class Mp4StructureTest {

    private fun detect(bytes: ByteArray) = AudioMetadataReader.detect(ByteArraySource(bytes))

    private val moov = Mp4.atom("moov", Mp4.mvhd(1000, 3000), Mp4.udta(Mp4.text("©nam", "Title")))

    @Test
    fun audiobookBrandIsMp4() {
        assertEquals(AudioFormat.Mp4, detect(Mp4.ftyp("M4B ") + moov))
        assertEquals(AudioFormat.Mp4, detect(Mp4.ftyp("M4A ", "M4A ", "mp42") + moov))
    }

    @Test
    fun compatibleBrandAloneIsMp4() {
        assertEquals(AudioFormat.Mp4, detect(Mp4.ftyp("XXXX", "abcd", "isom") + moov))
    }

    @Test
    fun unknownBrandsAreNotDetected() {
        assertNull(detect(Mp4.ftyp("heic", "mif1", "heic") + moov))
        assertNull(read(Mp4.ftyp("qt  ", "qt  ") + moov))
    }

    @Test
    fun ftypAfterId3IsMp4AndStillParsed() {
        val bytes = Id3Builder(3).text("TIT2", "Ignored").build() + Mp4.ftyp() + moov
        val result = readMp4(bytes)
        assertEquals("Title", result.tags.title)
        assertEquals(3.seconds, result.duration)
    }

    @Test
    fun moovAfterHugeMdatIsReadWithoutReadingMdat() {
        val head = Mp4.ftyp() + Mp4.atom64("mdat", ByteArray(0), declaredSize = 2_000_000_000L)
        val moovAt = head.size - 16 + 2_000_000_000L
        val source = SparseSource(moovAt + moov.size, listOf(0L to head, moovAt to moov))
        val result = AudioMetadataReader.read(source)!!
        assertEquals("Title", result.tags.title)
        assertEquals(3.seconds, result.duration)
        assertTrue(source.bytesRead < 4096, "read ${source.bytesRead} bytes")
    }

    @Test
    fun sizeZeroAtomRunsToTheEnd() {
        val body = moov.copyOfRange(8, moov.size)
        val result = readMp4(Mp4.ftyp() + Mp4.atom("free") + u32(0) + latin1("moov") + body)
        assertEquals("Title", result.tags.title)
        assertEquals(3.seconds, result.duration)
    }

    @Test
    fun sixtyFourBitAtomsAreWalked() {
        val moov64 = Mp4.atom64("moov", Mp4.mvhd(1000, 3000) + Mp4.atom64("udta", Mp4.udta(Mp4.text("©nam", "Wide")).copyOfRange(8, Mp4.udta(Mp4.text("©nam", "Wide")).size)))
        val result = readMp4(Mp4.ftyp() + Mp4.atom64("free", ByteArray(4)) + moov64)
        assertEquals("Wide", result.tags.title)
        assertEquals(3.seconds, result.duration)
    }

    @Test
    fun quickTimeStyleMetaWithoutVersion() {
        val bytes = Mp4.ftyp() + Mp4.atom("moov", Mp4.udta(Mp4.text("©nam", "QT"), fullBox = false))
        assertEquals("QT", readMp4(bytes).tags.title)
    }

    @Test
    fun unknownAtomsAreSkipped() {
        val bytes = Mp4.ftyp() + Mp4.atom("wide") + Mp4.atom("junk", ByteArray(100)) +
            Mp4.atom("moov", Mp4.atom("iods", ByteArray(8)), Mp4.mvhd(1000, 3000), Mp4.atom("meta", ByteArray(20)))
        assertEquals(3.seconds, readMp4(bytes).duration)
    }

    @Test
    fun largeBookReadsOnlyItsHeaders() {
        // A 20-hour sound track at 44.1 kHz: about 860 000 AAC frames, so a 3.4 MB stsz and stco.
        val frames = (20 * 3600 * 44100L / 1024).toInt()
        val soundStbl = Mp4.atom(
            "stbl",
            Mp4.stts(frames to 1024), Mp4.stsc(1 to 1),
            Mp4.stsz(List(frames) { 300 }), Mp4.stco(List(frames) { 0L }),
        )
        val titles = List(300) { "Chapter ${it + 1}" }
        val samples = titles.map { Mp4.textSample(it) }
        val head = Mp4.file({ mdatAt ->
            val offsets = mutableListOf<Long>()
            var at = mdatAt
            samples.forEach { offsets += at; at += it.size }
            Mp4.atom(
                "moov",
                Mp4.mvhd(1000, 20 * 3600 * 1000L),
                Mp4.trak(1, "soun", 44100, frames * 1024L, chapters = listOf(2), stbl = soundStbl),
                Mp4.trak(2, "text", 1000, 20 * 3600 * 1000L, stbl = Mp4.atom("stbl", Mp4.stts(300 to 240_000), Mp4.stsc(1 to 1), Mp4.stsz(samples.map { it.size }), Mp4.stco(offsets))),
            )
        }, concat(samples))
        assertTrue(soundStbl.size > 4_000_000)
        val source = CountingSource(head, virtualSize = 500_000_000)
        val result = AudioMetadataReader.read(source)!!
        assertEquals(20.hours, result.duration)
        assertEquals(300, result.chapters.size)
        assertEquals("Chapter 300", result.chapters.last().title)
        assertEquals(20.hours, result.chapters.last().end)
        assertTrue(source.bytesRead < 64 * 1024, "read ${source.bytesRead} bytes")
    }
}
