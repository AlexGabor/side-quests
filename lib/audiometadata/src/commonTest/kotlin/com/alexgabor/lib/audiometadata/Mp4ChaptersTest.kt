package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Mp4ChaptersTest {

    @Test
    fun quickTimeChapters() {
        val result = readMp4(Mp4.chapterBook(listOf("One", "Two", "Three"), durationMs = 170_000))
        assertEquals(
            listOf(Chapter("One", 0.seconds, 60.seconds), Chapter("Two", 60.seconds, 120.seconds), Chapter("Three", 120.seconds, 170.seconds)),
            result.chapters,
        )
    }

    @Test
    fun neroChapters() {
        val bytes = Mp4.ftyp() + Mp4.atom("moov", Mp4.mvhd(1000, 3000), Mp4.atom("udta", Mp4.chpl(1500L to "Two", 0L to "One")))
        assertEquals(listOf(Chapter("One", 0.seconds, 1.5.seconds), Chapter("Two", 1.5.seconds, 3.seconds)), readMp4(bytes).chapters)
    }

    @Test
    fun neroVersion0() {
        val bytes = Mp4.ftyp() + Mp4.atom("moov", Mp4.mvhd(1000, 2000), Mp4.atom("udta", Mp4.chpl(0L to "Only", version = 0)))
        assertEquals(listOf(Chapter("Only", 0.seconds, 2.seconds)), readMp4(bytes).chapters)
    }

    @Test
    fun quickTimeWinsOverNero() {
        val result = readMp4(Mp4.chapterBook(listOf("Quick"), nero = listOf(0L to "Nero")))
        assertEquals(listOf("Quick"), result.chapters.map { it.title })
    }

    @Test
    fun co64AndSeveralSamplesPerChunk() {
        val titles = List(7) { "C$it" }
        for (perChunk in listOf(1, 2, 3, 7)) {
            val result = readMp4(Mp4.chapterBook(titles, deltaMs = 1000, perChunk = perChunk, wide = true))
            assertEquals(titles, result.chapters.map { it.title }, "perChunk=$perChunk")
            assertEquals(6.seconds, result.chapters.last().start)
        }
    }

    @Test
    fun stscRunsChangeSamplesPerChunk() {
        // Chunks of 2, 2, then 1 sample: stsc (1 → 2), (3 → 1).
        val samples = listOf("A", "B", "C", "D", "E").map { Mp4.textSample(it) }
        val bytes = Mp4.file({ mdatAt ->
            val starts = samples.runningFold(mdatAt) { at, s -> at + s.size }
            Mp4.atom(
                "moov", Mp4.mvhd(1000, 5000),
                Mp4.trak(1, "soun", chapters = listOf(2)),
                Mp4.trak(2, "text", 1000, 5000, stbl = Mp4.atom(
                    "stbl", Mp4.stts(5 to 1000), Mp4.stsc(1 to 2, 3 to 1),
                    Mp4.stsz(samples.map { it.size }), Mp4.stco(listOf(starts[0], starts[2], starts[4])),
                )),
            )
        }, concat(samples))
        assertEquals(listOf("A", "B", "C", "D", "E"), readMp4(bytes).chapters.map { it.title })
    }

    @Test
    fun unreferencedTextTrackGivesNoChapters() {
        assertEquals(emptyList(), readMp4(Mp4.chapterBook(listOf("One"), referenced = false)).chapters)
    }

    @Test
    fun utf16Titles() {
        val title = utf16("Ünïcødé ★", bigEndian = true)
        val samples = listOf(Mp4.textSample("", title))
        val bytes = Mp4.file({ mdatAt ->
            Mp4.atom(
                "moov", Mp4.mvhd(1000, 1000), Mp4.trak(1, "soun", chapters = listOf(2)),
                Mp4.trak(2, "text", 1000, 1000, stbl = Mp4.atom("stbl", Mp4.stts(1 to 1000), Mp4.stsc(1 to 1), Mp4.stsz(samples.map { it.size }), Mp4.stco(listOf(mdatAt)))),
            )
        }, concat(samples))
        assertEquals("Ünïcødé ★", readMp4(bytes).chapters.single().title)
    }

    @Test
    fun emptyTitleIsAbsent() {
        assertEquals(listOf(null, "Two"), readMp4(Mp4.chapterBook(listOf("", "Two"))).chapters.map { it.title })
    }

    @Test
    fun lastChapterWithoutDurationEndsAfterItsSample() {
        val bytes = Mp4.chapterBook(listOf("One", "Two"), deltaMs = 1000, durationMs = 0)
        val result = readMp4(bytes)
        assertEquals(listOf(Chapter("One", 0.seconds, 1.seconds), Chapter("Two", 1.seconds, 2.seconds)), result.chapters)
    }

    @Test
    fun largeChunksAreReadSampleBySample() {
        // 100 samples of 4 KB each: more than one coalesced read allows, and each title read is capped.
        val samples = List(100) { Mp4.textSample("S$it") + ByteArray(4000) }
        val bytes = Mp4.file({ mdatAt ->
            Mp4.atom(
                "moov", Mp4.mvhd(1000, 100_000), Mp4.trak(1, "soun", chapters = listOf(2)),
                Mp4.trak(2, "text", 1000, 100_000, stbl = Mp4.atom("stbl", Mp4.stts(100 to 1000), Mp4.stsc(1 to 100), Mp4.stsz(samples.map { it.size }), Mp4.stco(listOf(mdatAt)))),
            )
        }, concat(samples))
        val source = CountingSource(bytes)
        val chapters = AudioMetadataReader.read(source)!!.chapters
        assertEquals(List(100) { "S$it" }, chapters.map { it.title })
        assertEquals(99.seconds, chapters.last().start)
        assertTrue(source.bytesRead < bytes.size / 3, "read ${source.bytesRead} of ${bytes.size}")
    }

    @Test
    fun millisecondPrecision() {
        val result = readMp4(Mp4.chapterBook(listOf("A", "B"), deltaMs = 1234))
        assertEquals(1234.milliseconds, result.chapters[1].start)
    }
}
