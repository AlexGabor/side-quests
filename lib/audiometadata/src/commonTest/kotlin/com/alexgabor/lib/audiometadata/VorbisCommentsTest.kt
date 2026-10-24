package com.alexgabor.lib.audiometadata

import com.alexgabor.lib.audiometadata.vorbis.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The comment mapping FLAC and Ogg share, read through an Opus file. */
class VorbisCommentsTest {

    private fun opus(vararg comments: String, granule: Long = 312L + 3 * 60 * 48000) =
        readAs(AudioFormat.Opus, Ogg.stream(Ogg.opusHead(312), Ogg.opusTags(vorbisComments(*comments)), granule))

    @Test
    fun fullTags() {
        val result = opus(
            "TITLE=T", "ALBUM=A", "ARTIST=R", "ALBUMARTIST=B", "COMPOSER=C", "NARRATOR=N", "GENRE=Audiobook",
            "DATE=2021-05-01", "TRACKNUMBER=3/12", "DISCNUMBER=1", "DISCTOTAL=2",
        )
        assertEquals(
            AudioTags(
                title = "T", album = "A", artist = "R", albumArtist = "B", narrator = "N", composer = "C",
                genre = "Audiobook", year = 2021, trackNumber = 3, trackTotal = 12, discNumber = 1, discTotal = 2,
            ),
            result.tags,
        )
    }

    @Test
    fun keysIgnoreCaseAndValuesAreTrimmed() {
        assertEquals("Title", opus("title=  Title ").tags.title)
    }

    @Test
    fun albumArtistSpellings() {
        assertEquals("B", opus("ALBUM ARTIST=B").tags.albumArtist)
        assertEquals("B", opus("album_artist=B").tags.albumArtist)
    }

    @Test
    fun performerAsNarrator() {
        assertEquals("Jane Doe", opus("PERFORMER=Jane Doe").tags.narrator)
    }

    @Test
    fun narratorWinsOverPerformer() {
        assertEquals("N", opus("performer=P", "Narrated By=N").tags.narrator)
        assertEquals("N", opus("NARRATEDBY=N", "PERFORMER=P").tags.narrator)
    }

    @Test
    fun composerIsNeverTheNarrator() {
        val tags = opus("COMPOSER=C").tags
        assertEquals("C", tags.composer)
        assertNull(tags.narrator)
    }

    @Test
    fun repeatedKey() {
        assertEquals("One; Two", opus("ARTIST=One", "ARTIST=Two").tags.artist)
    }

    @Test
    fun totalsFromTheirOwnKeys() {
        val tags = opus("TRACKNUMBER=4", "TOTALTRACKS=9", "DISCNUMBER=2", "TOTALDISCS=3").tags
        assertEquals(listOf(4, 9, 2, 3), listOf(tags.trackNumber, tags.trackTotal, tags.discNumber, tags.discTotal))
        assertEquals(10, opus("TRACKNUMBER=4", "TRACKTOTAL=10").tags.trackTotal)
    }

    @Test
    fun yearFallbacks() {
        assertEquals(1999, opus("YEAR=1999").tags.year)
        assertEquals(1950, opus("ORIGINALDATE=1950-02-02").tags.year)
        assertEquals(2001, opus("DATE=2001", "YEAR=1999").tags.year)
    }

    @Test
    fun entryWithoutEqualsIsSkipped() {
        assertEquals("T", opus("garbage", "=empty key", "TITLE=T").tags.title)
    }

    @Test
    fun cutOffList() {
        // Two entries are counted, but the second declares 1000 bytes and only 7 follow.
        val fixed = le32(4) + utf8("test") + le32(2) + le32(7) + utf8("TITLE=T") + le32(1000) + utf8("ALBUM=A")
        val result = readAs(AudioFormat.Opus, Ogg.stream(Ogg.opusHead(0), Ogg.opusTags(fixed), 48000))
        assertEquals("T", result.tags.title)
        assertNull(result.tags.album)
    }

    @Test
    fun hugeCount() {
        val comments = vorbisComments("TITLE=T", count = 0xFFFFFFFFL)
        assertEquals("T", readAs(AudioFormat.Opus, Ogg.stream(Ogg.opusHead(0), Ogg.opusTags(comments), 48000)).tags.title)
    }

    @Test
    fun pictureComment() {
        val back = base64(flacPicture(4, "image/jpeg", JPEG))
        val front = base64(flacPicture(3, "image/png", PNG))
        val result = opus("METADATA_BLOCK_PICTURE=$back", "METADATA_BLOCK_PICTURE=$front")
        assertEquals(Cover(PNG, "image/png"), result.cover)
    }

    @Test
    fun pictureWithoutMimeIsSniffed() {
        val result = opus("METADATA_BLOCK_PICTURE=" + base64(flacPicture(3, "", JPEG)))
        assertEquals(Cover(JPEG, "image/jpeg"), result.cover)
    }

    @Test
    fun linkedPictureIsIgnored() {
        assertNull(opus("METADATA_BLOCK_PICTURE=" + base64(flacPicture(3, "-->", utf8("http://x/y.png")))).cover)
    }

    @Test
    fun legacyCover() {
        val wrapped = base64(JPEG).chunked(4).joinToString("\r\n")
        assertEquals(Cover(JPEG, "image/jpeg"), opus("COVERART=$wrapped").cover)
        assertEquals(Cover(JPEG, "image/x-test"), opus("COVERART=${base64(JPEG)}", "COVERARTMIME=image/x-test").cover)
    }

    @Test
    fun pictureBlockWinsOverLegacyCover() {
        val result = opus("COVERART=${base64(JPEG)}", "METADATA_BLOCK_PICTURE=" + base64(flacPicture(3, "image/png", PNG)))
        assertEquals(Cover(PNG, "image/png"), result.cover)
    }

    @Test
    fun brokenPictureIsSkipped() {
        val result = opus("METADATA_BLOCK_PICTURE=AAAA", "METADATA_BLOCK_PICTURE=" + base64(flacPicture(0, "image/png", PNG)))
        assertEquals(Cover(PNG, "image/png"), result.cover)
    }

    @Test
    fun chapterComments() {
        val result = opus("CHAPTER001=00:00:00.000", "CHAPTER001NAME=One", "CHAPTER002=00:01:30.500", "CHAPTER002NAME=Two")
        assertEquals(3.minutes, result.duration)
        assertEquals(
            listOf(Chapter("One", 0.seconds, 90.5.seconds), Chapter("Two", 90.5.seconds, 3.minutes)),
            result.chapters,
        )
    }

    @Test
    fun chapterTimeForms() {
        val result = opus(
            "CHAPTER2=01:00.25", "CHAPTER10=1:00:00", "CHAPTER10NAME=Hour", "CHAPTER3=bad", "CHAPTER3NAME=Bad",
            "CHAPTER4NAME=No time", granule = 312L + 2 * 3600 * 48000,
        )
        assertEquals(
            listOf(Chapter(null, 60.25.seconds, 60.minutes), Chapter("Hour", 60.minutes, 120.minutes)),
            result.chapters,
        )
    }

    @Test
    fun chaptersInFileOrderAreSorted() {
        val result = opus("CHAPTER002=00:00:10.000", "CHAPTER001=00:00:00.000", "CHAPTER002NAME=B", "CHAPTER001NAME=A")
        assertEquals(listOf("A", "B"), result.chapters.map { it.title })
        assertEquals(10.seconds, result.chapters[0].end)
    }

    @Test
    fun lastChapterWithoutDuration() {
        val result = readAs(
            AudioFormat.Opus,
            Ogg.stream(Ogg.opusHead(312), Ogg.opusTags(vorbisComments("CHAPTER1=00:00:05.000")), granule = -1),
        )
        assertNull(result.duration)
        assertEquals(listOf(Chapter(null, 5.seconds, 5.seconds)), result.chapters)
    }

    @Test
    fun lenientBase64() {
        assertContentEquals(utf8("hello"), Base64.decode("aGVs\n bG8"))
        assertContentEquals(utf8("hello"), Base64.decode("aGVsbG8="))
        assertContentEquals(bytesOf(0xFB, 0xFF), Base64.decode("-_8"))
        assertContentEquals(ByteArray(0), Base64.decode("!!"))
    }
}
