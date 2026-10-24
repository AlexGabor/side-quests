package com.alexgabor.lib.audiometadata

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The FLAC, Ogg, WAV, AAC and cue fixtures written by `scripts/generate-fixtures.sh`. Durations and
 * chapters match ffprobe's.
 */
class OtherFormatsFixturesTest {

    private fun fixture(name: String): File =
        File(requireNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.toURI())

    private fun read(name: String): AudioMetadata = FileSource(fixture(name)).use { AudioMetadataReader.read(it) }
        ?: error("$name not recognised")

    private val png = fixture("mp3/cover.png").readBytes()

    private val fullTags = AudioTags(
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

    private val chapters = listOf(
        Chapter("Opening", 0.seconds, 1.seconds),
        Chapter("Middle", 1.seconds, 2.seconds),
        Chapter("Ending", 2.seconds, 3.seconds),
    )

    @Test
    fun flacTagsCoverAndCueSheet() {
        val result = read("flac/tags.flac")
        assertEquals(AudioFormat.Flac, result.format)
        assertEquals(fullTags, result.tags)
        assertEquals(Cover(png, "image/png"), result.cover)
        assertEquals(3.seconds, result.duration)
        // CUESHEET block times, titled by the CUESHEET comment, the last ending at the lead-out.
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun flacBehindId3() {
        val result = read("flac/id3.flac")
        assertEquals(AudioFormat.Flac, result.format)
        assertEquals(AudioTags(title = "Flac Title", album = "Id3 Album"), result.tags)
        // The FLAC has no picture, so the ID3 tag's fills the gap; its chapters are not used.
        assertEquals(Cover(png, "image/png"), result.cover)
        assertEquals(1.seconds, result.duration)
        assertEquals(emptyList(), result.chapters)
    }

    @Test
    fun oggVorbis() {
        val result = read("ogg/tags.ogg")
        assertEquals(AudioFormat.Vorbis, result.format)
        assertEquals(fullTags, result.tags)
        assertEquals(Cover(png, "image/png"), result.cover)
        assertEquals(3.seconds, result.duration)
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun oggOpus() {
        val result = read("ogg/tags.opus")
        assertEquals(AudioFormat.Opus, result.format)
        assertEquals(fullTags, result.tags)
        assertEquals(Cover(png, "image/png"), result.cover)
        // The last granule is 144 312, less the 312-sample pre-skip, at 48 kHz. ffprobe adds its own
        // start-time offset and says 3.0065 s.
        assertEquals(3.seconds, result.duration)
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun wavInfo() {
        val result = read("wav/info.wav")
        assertEquals(
            AudioMetadata(
                AudioFormat.Wav,
                AudioTags(title = "Info Title", album = "Fixture Album", artist = "Fixture Artist", genre = "Audiobook", year = 2019, trackNumber = 4),
                duration = 1.seconds,
            ),
            result,
        )
    }

    @Test
    fun wavId3Chunk() {
        val result = read("wav/id3.wav")
        assertEquals(
            AudioTags(
                title = "Id3 Title",
                album = "Fixture Album",
                artist = "Fixture Artist",
                albumArtist = "Fixture Author",
                genre = "Audiobook",
                year = 2019,
                trackNumber = 4,
            ),
            result.tags,
        )
        assertEquals(Cover(png, "image/png"), result.cover)
        assertEquals(1.seconds, result.duration)
        // CHAP frames carry their own end times, past this one-second file.
        assertEquals(chapters, result.chapters)
    }

    @Test
    fun adts() {
        val plain = read("aac/plain.aac")
        // 25 frames of 1024 samples at 8 kHz, as ffprobe counts them.
        assertEquals(AudioMetadata(AudioFormat.Aac, duration = 3.2.seconds), plain)
        val behindId3 = read("aac/id3.aac")
        // Duration only: the ID3 tag in front is skipped, not read.
        assertEquals(AudioMetadata(AudioFormat.Aac, duration = 3.2.seconds), behindId3)
    }

    @Test
    fun cueSheet() {
        val sheet = assertNotNull(FileSource(fixture("cue/book.cue")).use { CueSheet.read(it) })
        assertEquals("Café Stories", sheet.title)
        assertEquals("Fixture Author", sheet.performer)
        assertEquals(listOf("Part 1.wav", "Part 2.wav"), sheet.files.map { it.name })
        assertEquals(
            CueSheet.Track(2, "Brücke – Middle", "Guest Reader", 1.minutes),
            sheet.files[0].tracks[1],
        )
        // Paired with the encoded file: the extension differs.
        assertEquals(
            listOf(Chapter("Opening", 0.seconds, 1.minutes), Chapter("Brücke – Middle", 1.minutes, 5.minutes)),
            sheet.chapters("Part 1.flac", 5.minutes),
        )
        // Track 3 has only INDEX 00; track 4 has no index at all and gives no chapter.
        assertEquals(listOf(Chapter("Ending", 0.seconds, 2.minutes)), sheet.chapters("/books/x/part 2.WAV", 2.minutes))
        assertEquals(emptyList(), sheet.chapters("Part 3.flac", 2.minutes))
        // INDEX 00 of track 2, 00:59:70, is 59 s and 70 frames.
        assertEquals((59.seconds + (70 * 1_000_000_000L / 75).nanoseconds), CueSheet.parse(
            "TRACK 01 AUDIO\nINDEX 00 00:59:70".encodeToByteArray(),
        )?.files?.single()?.tracks?.single()?.start)
    }

    @Test
    fun cueSheetIsNotAudio() {
        assertNull(FileSource(fixture("cue/book.cue")).use { AudioMetadataReader.detect(it) })
    }
}
