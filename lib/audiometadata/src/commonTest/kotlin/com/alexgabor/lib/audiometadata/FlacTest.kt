package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FlacTest {

    private fun flac(vararg blocks: Pair<Int, ByteArray>) = readAs(AudioFormat.Flac, Flac.file(*blocks))

    private val info = Flac.STREAMINFO to Flac.streamInfo(44100, 44100L * 30)

    private fun comments(vararg entries: String) = Flac.VORBIS_COMMENT to vorbisComments(*entries)

    @Test
    fun tagsCoverAndDuration() {
        val result = flac(
            Flac.STREAMINFO to Flac.streamInfo(8000, 24000),
            comments("TITLE=T"),
            Flac.PICTURE to flacPicture(4, "image/jpeg", JPEG),
            Flac.PICTURE to flacPicture(3, "image/png", PNG),
        )
        assertEquals(3.seconds, result.duration)
        assertEquals("T", result.tags.title)
        assertEquals(Cover(PNG, "image/png"), result.cover)
    }

    @Test
    fun firstPictureWithoutFrontCover() {
        val result = flac(info, Flac.PICTURE to flacPicture(4, "image/jpg", JPEG), Flac.PICTURE to flacPicture(5, "image/png", PNG))
        assertEquals(Cover(JPEG, "image/jpeg"), result.cover)
    }

    @Test
    fun emptyPictureIsIgnored() {
        assertNull(flac(info, Flac.PICTURE to flacPicture(3, "image/png", ByteArray(0))).cover)
    }

    @Test
    fun unknownTotal() {
        val result = flac(Flac.STREAMINFO to Flac.streamInfo(44100, 0), comments("TITLE=T"))
        assertNull(result.duration)
        assertEquals("T", result.tags.title)
    }

    @Test
    fun longTotalNeeds36Bits() {
        // 30 hours at 48 kHz is more than 32 bits of samples.
        val samples = 48000L * 3600 * 30
        assertTrue(samples > 0xFFFFFFFFL)
        assertEquals((30 * 3600).seconds, flac(Flac.STREAMINFO to Flac.streamInfo(48000, samples)).duration)
    }

    @Test
    fun paddingAndUnknownBlocksAreSkipped() {
        val result = flac(info, Flac.PADDING to ByteArray(8192), 2 to ByteArray(10), comments("ALBUM=A"))
        assertEquals("A", result.tags.album)
    }

    @Test
    fun leadingId3FillsGaps() {
        val id3 = Id3Builder(3).text("TIT2", "Old").text("TALB", "Album").frame("APIC", apicBody("image/jpeg", 3, JPEG))
            .chap("c1", 0, 1000, "Id3 chapter").ctoc("toc", listOf("c1"), topLevel = true).build()
        val file = id3 + Flac.file(info, comments("TITLE=New"), Flac.PICTURE to flacPicture(4, "image/png", PNG))
        val result = readAs(AudioFormat.Flac, file)
        assertEquals("New", result.tags.title)
        assertEquals("Album", result.tags.album)
        // The FLAC has a picture, so the ID3 front cover doesn't replace it; ID3 chapters aren't used.
        assertEquals(Cover(PNG, "image/png"), result.cover)
        assertEquals(emptyList(), result.chapters)
    }

    @Test
    fun leadingId3CoverWhenFlacHasNone() {
        val id3 = Id3Builder(4).frame("APIC", apicBody("image/jpeg", 3, JPEG)).build()
        assertEquals(Cover(JPEG, "image/jpeg"), readAs(AudioFormat.Flac, id3 + Flac.file(info)).cover)
    }

    private val cueTracks = listOf(
        Flac.Track(0, 1),
        Flac.Track(441_000, 2),
        Flac.Track(882_000, 3),
        Flac.Track(1_323_000, 170, indexes = emptyList()),
    )

    private val cueText = """
        TITLE "Book"
        FILE "book.wav" WAVE
          TRACK 01 AUDIO
            TITLE "A"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "B"
            INDEX 01 00:10:00
          TRACK 03 AUDIO
            TITLE "C"
            INDEX 01 00:20:00
    """.trimIndent()

    @Test
    fun cueSheetBlockWithTitles() {
        val result = flac(
            Flac.STREAMINFO to Flac.streamInfo(44100, 44100L * 40),
            comments("CUESHEET=$cueText"),
            Flac.CUESHEET to Flac.cueSheet(cueTracks, cd = true),
        )
        assertEquals(
            listOf(Chapter("A", 0.seconds, 10.seconds), Chapter("B", 10.seconds, 20.seconds), Chapter("C", 20.seconds, 30.seconds)),
            result.chapters,
        )
    }

    @Test
    fun cueSheetBlockWithoutTitles() {
        val result = flac(Flac.STREAMINFO to Flac.streamInfo(44100, 44100L * 40), Flac.CUESHEET to Flac.cueSheet(cueTracks, cd = true))
        assertEquals(
            listOf(Chapter(null, 0.seconds, 10.seconds), Chapter(null, 10.seconds, 20.seconds), Chapter(null, 20.seconds, 30.seconds)),
            result.chapters,
        )
    }

    @Test
    fun nonCdLeadOutIs255() {
        val tracks = listOf(Flac.Track(0, 1), Flac.Track(44_100, 170), Flac.Track(88_200, 255, emptyList()))
        val result = flac(info, Flac.CUESHEET to Flac.cueSheet(tracks, cd = false))
        // On a non-CD sheet, 170 is an ordinary track.
        assertEquals(listOf(Chapter(null, 0.seconds, 1.seconds), Chapter(null, 1.seconds, 2.seconds)), result.chapters)
    }

    @Test
    fun indexOneOffset() {
        val tracks = listOf(
            Flac.Track(0, 1),
            Flac.Track(441_000, 2, indexes = listOf(0 to 0L, 1 to 44_100L)),
            Flac.Track(882_000, 3, indexes = listOf(0 to 22_050L)),
            Flac.Track(1_323_000, 170, emptyList()),
        )
        val result = flac(info, Flac.CUESHEET to Flac.cueSheet(tracks, cd = true))
        assertEquals(listOf(0.seconds, 11.seconds, 20.5.seconds), result.chapters.map { it.start })
    }

    @Test
    fun chapterCommentsWin() {
        val result = flac(
            info,
            comments("CHAPTER001=00:00:00.000", "CHAPTER001NAME=Comment"),
            Flac.CUESHEET to Flac.cueSheet(cueTracks, cd = true),
        )
        assertEquals(listOf(Chapter("Comment", 0.seconds, 30.seconds)), result.chapters)
    }

    @Test
    fun cueSheetCommentAlone() {
        val result = flac(info, comments("CUESHEET=$cueText"))
        assertEquals(listOf("A", "B", "C"), result.chapters.map { it.title })
        assertEquals(30.seconds, result.chapters.last().end)
    }

    @Test
    fun largeFileReadsLittle() {
        val head = Flac.file(info, comments("TITLE=T"), Flac.PICTURE to flacPicture(3, "image/png", PNG), Flac.PADDING to ByteArray(4000))
        val source = CountingSource(head, 500L * 1024 * 1024)
        val result = AudioMetadataReader.read(source)!!
        assertEquals("T", result.tags.title)
        assertTrue(source.bytesRead < 64 * 1024, "read ${source.bytesRead} bytes")
    }
}
