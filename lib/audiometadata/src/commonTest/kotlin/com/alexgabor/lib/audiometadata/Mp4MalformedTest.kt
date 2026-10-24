package com.alexgabor.lib.audiometadata

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Mp4MalformedTest {

    private val full = Mp4.ftyp() + Mp4.atom(
        "moov",
        Mp4.mvhd(1000, 3000),
        Mp4.udta(Mp4.text("©nam", "Title"), Mp4.text("©alb", "Album"), Mp4.text("©ART", "Artist"), Mp4.item("covr", 14, PNG)),
    )

    @Test
    fun truncatedMoovKeepsTheItemsBeforeTheCut() {
        // Cut inside the third item.
        val cut = full.copyOf(full.indexOf("©ART") + 12)
        val result = readMp4(cut)
        assertEquals(3.seconds, result.duration)
        assertEquals("Title", result.tags.title)
        assertEquals("Album", result.tags.album)
        assertNull(result.tags.artist)
        assertNull(result.cover)
    }

    @Test
    fun everyPrefixParsesWithoutThrowing() {
        val book = Mp4.chapterBook(listOf("One", "Two"), nero = listOf(0L to "N1", 1000L to "N2"))
        for (n in 0..book.size) read(book.copyOf(n))
    }

    @Test
    fun atomSmallerThanItsHeaderStopsTheLevel() {
        val bytes = Mp4.ftyp() + Mp4.atom("moov", Mp4.mvhd(1000, 3000), u32(4) + latin1("bad!"), Mp4.udta(Mp4.text("©nam", "Lost")))
        val result = readMp4(bytes)
        assertEquals(3.seconds, result.duration)
        assertNull(result.tags.title)
    }

    @Test
    fun hugeAndNegativeSizesDoNotAllocate() {
        val huge64 = Mp4.ftyp() + u32(1) + latin1("moov") + u64(Long.MAX_VALUE) + Mp4.mvhd(1000, 3000)
        assertEquals(3.seconds, readMp4(huge64).duration)
        val negative = Mp4.ftyp() + u32(1) + latin1("moov") + u64(-16) + Mp4.mvhd(1000, 3000)
        assertEquals(AudioMetadata(AudioFormat.Mp4), readMp4(negative))
        val hugeIlst = Mp4.ftyp() + Mp4.atom("moov", Mp4.atom("udta", Mp4.atom("meta", u32(0), u32(0xFFFFFFF0) + latin1("ilst"))))
        assertEquals(AudioMetadata(AudioFormat.Mp4), readMp4(hugeIlst))
    }

    @Test
    fun brokenItemsAreSkippedOneByOne() {
        val result = readMp4(
            Mp4.ftyp() + Mp4.atom(
                "moov",
                Mp4.udta(
                    Mp4.atom("©nam", Mp4.atom("data", bytesOf(1))),
                    Mp4.item("trkn", 0, bytesOf(1)),
                    Mp4.atom("covr"),
                    Mp4.atom("----", Mp4.atom("name", u32(0))),
                    Mp4.text("©alb", "Album"),
                ),
            ),
        )
        assertEquals(AudioTags(album = "Album"), result.tags)
    }

    @Test
    fun brokenChapterTrackFallsBackToNero() {
        val bytes = Mp4.file({
            Mp4.atom(
                "moov", Mp4.mvhd(1000, 2000),
                Mp4.trak(1, "soun", chapters = listOf(2)),
                Mp4.trak(2, "text", 1000, 2000, stbl = Mp4.atom("stbl", Mp4.stts(1 to 2000), Mp4.stsc(1 to 1), Mp4.stsz(listOf(10)), Mp4.stco(listOf(0xFFFFFF00L)))),
                Mp4.atom("udta", Mp4.chpl(0L to "Nero")),
            )
        })
        assertEquals(listOf(Chapter("Nero", 0.seconds, 2.seconds)), readMp4(bytes).chapters)
    }

    @Test
    fun hostileSampleCountsAreBounded() {
        val bytes = Mp4.file({ mdatAt ->
            Mp4.atom(
                "moov", Mp4.mvhd(1000, 2000),
                Mp4.trak(1, "soun", chapters = listOf(2)),
                Mp4.trak(2, "text", 1000, 2000, stbl = Mp4.atom(
                    "stbl", Mp4.stts(Int.MAX_VALUE to 1), Mp4.stsc(1 to Int.MAX_VALUE),
                    Mp4.stszUniform(5, 0xFFFFFFFFL), Mp4.stco(listOf(mdatAt)),
                )),
            )
        }, Mp4.textSample("abc"))
        val chapters = readMp4(bytes).chapters
        assertEquals(10_000, chapters.size)
        assertEquals("abc", chapters.first().title)
    }

    @Test
    fun chapterReferenceToASoundTrackIsIgnored() {
        val bytes = Mp4.ftyp() + Mp4.atom("moov", Mp4.mvhd(1000, 2000), Mp4.trak(1, "soun", chapters = listOf(1)))
        assertEquals(emptyList(), readMp4(bytes).chapters)
    }

    @Test
    fun failingSource() {
        assertNull(AudioMetadataReader.read(FailingSource))
    }

    @Test
    fun randomMutationsNeverThrow() {
        val book = Mp4.chapterBook(listOf("One", "Two", "Three"), nero = listOf(0L to "N")) +
            Mp4.atom("moov", Mp4.udta(Mp4.text("©nam", "T"), Mp4.item("covr", 13, JPEG)))
        val random = Random(5)
        repeat(2000) {
            val mutated = book.copyOf()
            repeat(random.nextInt(1, 8)) { mutated[random.nextInt(12, mutated.size)] = random.nextInt(256).toByte() }
            read(mutated)
        }
    }

    @Test
    fun randomBytesAfterFtypNeverThrow() {
        val random = Random(9)
        repeat(1000) {
            val result = read(Mp4.ftyp() + random.nextBytes(random.nextInt(0, 512)))
            assertTrue(result == null || result.format == AudioFormat.Mp4)
        }
    }

    private fun ByteArray.indexOf(s: String): Int {
        val needle = latin1(s)
        return (0..size - needle.size).first { i -> needle.indices.all { this[i + it] == needle[it] } }
    }
}
