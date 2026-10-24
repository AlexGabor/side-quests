package com.alexgabor.lib.audiometadata

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/** The MP4 clips written by `scripts/generate-fixtures.sh`. Durations and chapters match ffprobe's. */
class Mp4FixturesTest {

    private fun fixture(name: String): File =
        File(requireNotNull(javaClass.getResource("/fixtures/mp4/$name")) { "missing fixture $name" }.toURI())

    private fun read(name: String): AudioMetadata = FileSource(fixture(name)).use { AudioMetadataReader.read(it) }
        ?: error("$name not recognised")

    private val chapters = listOf(
        Chapter("Opening", 0.seconds, 1.seconds),
        Chapter("Middle", 1.seconds, 2.seconds),
        Chapter("Ending", 2.seconds, 3.seconds),
    )

    @Test
    fun tagsCoverAndBothChapterStyles() {
        val result = read("tags-both.m4b")
        assertEquals(AudioFormat.Mp4, result.format)
        assertEquals(
            AudioTags(
                title = "Bookplate Fixture",
                album = "Fixture Album",
                artist = "Fixture Artist",
                albumArtist = "Fixture Author",
                narrator = "Fixture Narrator",
                composer = "Fixture Composer",
                genre = "Audiobook",
                year = 2021,
                trackNumber = 3,
                trackTotal = 12,
                discNumber = 1,
                discTotal = 2,
            ),
            result.tags,
        )
        assertEquals(Cover(File(fixture("../mp3/cover.png").path).readBytes(), "image/png"), result.cover)
        assertEquals(3.seconds, result.duration)
        // The chpl titles are upper-cased in this file: these are the QuickTime ones.
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun quickTimeChaptersOnly() {
        val result = read("chapters-quicktime.m4a")
        assertEquals("QuickTime Chapters", result.tags.title)
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun neroChaptersOnly() {
        val result = read("chapters-nero.m4a")
        assertEquals("Nero Chapters", result.tags.title)
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun alac() {
        val result = read("alac.m4a")
        assertEquals(AudioTags(title = "Lossless", artist = "Fixture Artist", genre = "Speech", trackNumber = 1), result.tags)
        assertEquals(1.seconds, result.duration)
        assertEquals(emptyList(), result.chapters)
    }

    @Test
    fun plainMp4() {
        val result = read("plain.mp4")
        assertEquals(AudioMetadata(AudioFormat.Mp4, duration = 2.seconds), result)
    }

    @Test
    fun formatComesFromContentNotName() {
        val renamed = File.createTempFile("not-audio", ".mp3").apply {
            deleteOnExit()
            writeBytes(fixture("tags-both.m4b").readBytes())
        }
        assertEquals(AudioFormat.Mp4, FileSource(renamed).use { AudioMetadataReader.detect(it) })
    }

    /**
     * Five hours, 300 chapters: the file is the real header cut to 16 KB, served at its declared size
     * (what the `mdat` says) so the parser sees the whole file it describes.
     */
    @Test
    fun multiHourBookHeaderParsesQuicklyAndReadsLittle() {
        val head = fixture("long-header.m4b").readBytes()
        val mdat = (0..head.size - 4).first { head[it] == 'm'.code.toByte() && String(head, it, 4, Charsets.ISO_8859_1) == "mdat" } - 4
        val declared = mdat + (0..3).fold(0L) { acc, i -> acc shl 8 or (head[mdat + i].toLong() and 0xFF) }

        // Warm up the JIT, then time a cold read through a fresh source.
        repeat(20) { AudioMetadataReader.read(CountingSource(head, declared)) }
        val source = CountingSource(head, declared)
        val (result, took) = measureTimedValue { AudioMetadataReader.read(source)!! }
        println("long-header.m4b: declared ${declared} bytes, read ${source.bytesRead} bytes in $took")

        assertEquals(AudioFormat.Mp4, result.format)
        assertEquals("Long Book", result.tags.title)
        assertEquals(18001.92.seconds, result.duration)
        // ffmpeg's chpl stops at 255; the QuickTime track has all 300.
        assertEquals(300, result.chapters.size)
        assertEquals(Chapter("Chapter 1", 0.seconds, 1.minutes), result.chapters.first())
        assertEquals(Chapter("Chapter 300", 299.minutes, 18001.92.seconds), result.chapters.last())
        assertTrue(source.bytesRead < 16 * 1024, "read ${source.bytesRead} bytes")
        assertTrue(took < 50.milliseconds, "took $took")
    }

    @Test
    fun coverIsSharedWithMp3Fixtures() {
        assertNull(FileSource(fixture("../mp3/cover.png")).use { AudioMetadataReader.detect(it) })
    }
}
