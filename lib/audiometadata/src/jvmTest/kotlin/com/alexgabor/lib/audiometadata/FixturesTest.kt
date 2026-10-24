package com.alexgabor.lib.audiometadata

import java.io.RandomAccessFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The clips written by `scripts/generate-fixtures.sh`. Expected durations are ffprobe's, which also
 * reads the Xing/Info header but accounts for encoder padding differently; within 60 ms is a match.
 */
class FixturesTest {

    private fun fixture(name: String): File =
        File(requireNotNull(javaClass.getResource("/fixtures/mp3/$name")) { "missing fixture $name" }.toURI())

    /** Reads through the file like a platform source would, rather than loading it whole. */
    private class FileSource(file: File) : ByteSource, AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        override val size: Long = raf.length()
        override fun read(position: Long, length: Int): ByteArray {
            if (position >= size) return ByteArray(0)
            val out = ByteArray(minOf(length.toLong(), size - position).toInt())
            raf.seek(position)
            raf.readFully(out)
            return out
        }
        override fun close() = raf.close()
    }

    private fun read(name: String): AudioMetadata = FileSource(fixture(name)).use { AudioMetadataReader.read(it) }
        ?: error("$name not recognised")

    private fun assertDuration(expected: Double, actual: Duration?) {
        val d = requireNotNull(actual) { "no duration" }
        assertTrue((d - expected.seconds).absoluteValue < 0.06.seconds, "expected ≈${expected}s, got $d")
    }

    private val cover by lazy { Cover(fixture("cover.png").readBytes(), "image/png") }

    private val chapters = listOf(
        Chapter("Opening", 0.seconds, 1.seconds),
        Chapter("Middle", 1.seconds, 2.seconds),
        Chapter("Ending", 2.seconds, 3.seconds),
    )

    private val tags = AudioTags(
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
    )

    @Test
    fun id3v24WithCoverChaptersAndInfoHeader() {
        val result = read("tags-v24.mp3")
        assertEquals(AudioFormat.Mp3, result.format)
        assertEquals(tags, result.tags)
        assertEquals(cover, result.cover)
        assertEquals(chapters, result.chapters)
        assertDuration(3.056327, result.duration)
    }

    @Test
    fun id3v23WithUtf16AndId3v1() {
        val result = read("tags-v23-id3v1.mp3")
        assertEquals(tags.copy(title = "Ünïcødé ★ Title"), result.tags)
        assertEquals(cover, result.cover)
        assertEquals(chapters, result.chapters)
        assertDuration(3.056327, result.duration)
    }

    @Test
    fun id3v1Only() {
        val result = read("id3v1-only.mp3")
        assertEquals(
            AudioTags(title = "Old Title", artist = "Old Artist", album = "Old Album", year = 1999, trackNumber = 7, genre = "Speech"),
            result.tags,
        )
        assertNull(result.cover)
        assertDuration(1.071020, result.duration)
    }

    @Test
    fun vbrWithXingHeader() {
        val result = read("vbr-xing.mp3")
        assertEquals(AudioTags(), result.tags)
        assertDuration(2.063673, result.duration)
    }

    @Test
    fun cbrWithoutHeaderIsEstimated() {
        val result = read("cbr-no-header.mp3")
        assertEquals(AudioMetadata(AudioFormat.Mp3, duration = result.duration), result)
        assertDuration(2.063750, result.duration)
    }

    @Test
    fun formatComesFromContentNotName() {
        val renamed = File.createTempFile("not-audio", ".txt").apply {
            deleteOnExit()
            writeBytes(fixture("tags-v24.mp3").readBytes())
        }
        assertEquals(AudioFormat.Mp3, FileSource(renamed).use { AudioMetadataReader.detect(it) })
        assertNull(FileSource(fixture("cover.png")).use { AudioMetadataReader.detect(it) })
    }
}
