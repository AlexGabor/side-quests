package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Id3v2Test {

    private fun tags(tag: ByteArray) = readMp3(tag + Mpeg.frames(4)).tags

    @Test
    fun eachVersionReadsTitle() {
        assertEquals("T", tags(Id3Builder(2).text("TT2", "T", encoding = 0).build()).title)
        assertEquals("T", tags(Id3Builder(3).text("TIT2", "T").build()).title)
        assertEquals("T", tags(Id3Builder(4).text("TIT2", "T").build()).title)
    }

    @Test
    fun eachEncodingDecodes() {
        val text = "Ünïcødé ★ 📖"
        assertEquals("Café", tags(Id3Builder(3).text("TIT2", "Café", encoding = 0).build()).title)
        assertEquals(text, tags(Id3Builder(3).text("TIT2", text, encoding = 1).build()).title)
        assertEquals(text, tags(Id3Builder(4).text("TIT2", text, encoding = 2).build()).title)
        assertEquals(text, tags(Id3Builder(4).text("TIT2", text, encoding = 3).build()).title)
        // UTF-16 with a big-endian BOM, and with no BOM at all (read as little-endian).
        val be = bytesOf(1) + utf16(text, bigEndian = true)
        assertEquals(text, tags(Id3Builder(3).frame("TIT2", be).build()).title)
        val noBom = bytesOf(1) + utf16(text, bigEndian = false, bom = false)
        assertEquals(text, tags(Id3Builder(3).frame("TIT2", noBom).build()).title)
    }

    @Test
    fun multipleValuesAreJoinedAndTerminatorsDropped() {
        val v24 = bytesOf(3) + utf8("A") + bytesOf(0) + utf8("B") + bytesOf(0)
        assertEquals("A; B", tags(Id3Builder(4).frame("TPE1", v24).build()).artist)
        // Each UTF-16 value carries its own BOM.
        val v23 = bytesOf(1) + utf16("A", false) + bytesOf(0, 0) + utf16("B", true) + bytesOf(0, 0)
        assertEquals("A; B", tags(Id3Builder(3).frame("TPE1", v23).build()).artist)
    }

    @Test
    fun everyFieldMapsFromV23AndV24() {
        for (major in listOf(3, 4)) {
            val tag = Id3Builder(major)
                .text("TIT2", "Title").text("TALB", "Album").text("TPE1", "Artist").text("TPE2", "Album Artist")
                .text("TCOM", "Composer").text("TCON", "Audiobook").text(if (major == 4) "TDRC" else "TYER", "2019")
                .text("TRCK", "3/12").text("TPOS", "1")
                .frame("TXXX", textBody("NARRATOR", 0).plus(bytesOf(0)).plus(latin1("Reader")))
                .build()
            assertEquals(
                AudioTags(
                    title = "Title", album = "Album", artist = "Artist", albumArtist = "Album Artist",
                    narrator = "Reader", composer = "Composer", genre = "Audiobook", year = 2019,
                    trackNumber = 3, trackTotal = 12, discNumber = 1, discTotal = null,
                ),
                tags(tag),
                "v2.$major",
            )
        }
    }

    @Test
    fun everyFieldMapsFromV22() {
        val tag = Id3Builder(2)
            .text("TT2", "Title").text("TAL", "Album").text("TP1", "Artist").text("TP2", "Album Artist")
            .text("TCM", "Composer").text("TCO", "(101)").text("TYE", "1984").text("TRK", "2/9").text("TPA", "2/3")
            .frame("TXX", bytesOf(0) + latin1("narrated by") + bytesOf(0) + latin1("Reader"))
            .build()
        assertEquals(
            AudioTags(
                title = "Title", album = "Album", artist = "Artist", albumArtist = "Album Artist",
                narrator = "Reader", composer = "Composer", genre = "Speech", year = 1984,
                trackNumber = 2, trackTotal = 9, discNumber = 2, discTotal = 3,
            ),
            tags(tag),
        )
    }

    @Test
    fun narratorNeedsAKnownDescription() {
        val other = textBody("MOOD", 0) + bytesOf(0) + latin1("calm")
        assertNull(tags(Id3Builder(3).frame("TXXX", other).build()).narrator)
        val utf16 = bytesOf(1) + utf16("Narrated_By", false) + bytesOf(0, 0) + utf16("Reader", false)
        assertEquals("Reader", tags(Id3Builder(3).frame("TXXX", utf16).build()).narrator)
    }

    @Test
    fun yearPrefersRecordingTimeWhateverTheOrder() {
        val tag = Id3Builder(4).text("TDRL", "2001").text("TDRC", "1999-05-02T10:00").build()
        assertEquals(1999, tags(tag).year)
        assertEquals(1970, tags(Id3Builder(3).text("TORY", "1970").build()).year)
    }

    @Test
    fun genreForms() {
        fun genre(value: String) = tags(Id3Builder(3).text("TCON", value).build()).genre
        assertEquals("Speech", genre("(101)"))
        assertEquals("Speech", genre("101"))
        assertEquals("Spoken Word", genre("(101)Spoken Word"))
        assertEquals("Remix", genre("(RX)"))
        assertEquals("(Weird)", genre("((Weird)"))
        assertEquals("Audiobook", genre("Audiobook"))
        assertEquals("Audiobook; Podcast", tags(Id3Builder(4).frame("TCON", bytesOf(3) + utf8("183\u0000186")).build()).genre)
    }

    @Test
    fun trackWithoutTotalAndJunkNumbers() {
        assertEquals(5, tags(Id3Builder(3).text("TRCK", "5").build()).trackNumber)
        val junk = tags(Id3Builder(3).text("TRCK", "x/y").text("TPOS", "0/0").build())
        assertNull(junk.trackNumber)
        assertNull(junk.discNumber)
        assertNull(junk.discTotal)
    }

    @Test
    fun blankValuesAreAbsent() {
        val t = tags(Id3Builder(3).text("TIT2", "  ").text("TALB", "  Album  ").build())
        assertNull(t.title)
        assertEquals("Album", t.album)
    }

    @Test
    fun wholeTagUnsynchronisationIsReversed() {
        for (major in listOf(2, 3)) {
            val id = if (major == 2) "PIC" else "APIC"
            val body = if (major == 2) picBody("JPG", 3, JPEG) else apicBody("image/jpeg", 3, JPEG)
            val tag = Id3Builder(major).text(if (major == 2) "TT2" else "TIT2", "T").frame(id, body).build(unsync = true)
            val result = readMp3(tag + Mpeg.frames(2))
            assertContentEquals(JPEG, result.cover?.data, "v2.$major")
            assertEquals("T", result.tags.title)
        }
    }

    @Test
    fun perFrameUnsynchronisationIn24() {
        val body = unsynchronise(apicBody("image/jpeg", 3, JPEG))
        val byFrameFlag = Id3Builder(4).frame("APIC", body, format = 0x02).build()
        assertContentEquals(JPEG, readMp3(byFrameFlag).cover?.data)
        // Only the tag flag set: every frame is still unsynchronised.
        val byTagFlag = Id3Builder(4).frame("APIC", body).build(unsync = true)
        assertContentEquals(JPEG, readMp3(byTagFlag).cover?.data)
    }

    @Test
    fun extendedHeaderIsSkipped() {
        assertEquals("T", tags(Id3Builder(3).text("TIT2", "T").build(extendedHeader = true)).title)
        assertEquals("T", tags(Id3Builder(4).text("TIT2", "T").build(extendedHeader = true)).title)
        assertEquals("T", tags(Id3Builder(3).text("TIT2", "T").build(extendedHeader = true, unsync = true)).title)
    }

    @Test
    fun frameExtrasIn24() {
        // Grouping byte and data length indicator before the body.
        val body = bytesOf(7) + syncsafe(textBody("T").size) + textBody("T")
        assertEquals("T", tags(Id3Builder(4).frame("TIT2", body, format = 0x41).build()).title)
        // 2.3 grouping byte.
        assertEquals("T", tags(Id3Builder(3).frame("TIT2", bytesOf(7) + textBody("T", 0), format = 0x20).build()).title)
    }

    @Test
    fun compressedAndEncryptedFramesAreSkipped() {
        val tag = Id3Builder(3)
            .frame("TIT2", bytesOf(0, 0, 0, 9) + ByteArray(9), format = 0x80)
            .frame("TALB", bytesOf(1) + ByteArray(9), format = 0x40)
            .text("TPE1", "Artist")
            .build()
        val t = tags(tag)
        assertNull(t.title)
        assertNull(t.album)
        assertEquals("Artist", t.artist)
        val v24 = Id3Builder(4).frame("TIT2", ByteArray(10), format = 0x08).text("TPE1", "A").build()
        assertNull(tags(v24).title)
        assertEquals("A", tags(v24).artist)
    }

    @Test
    fun nonSyncsafeFrameSizesIn24() {
        // 200 bytes: syncsafe would read 0x00C8 as 0x48, landing mid-frame.
        val long = "x".repeat(199)
        val tag = Id3Builder(4).frame("TIT2", textBody(long), plainSize = true).text("TPE1", "Artist").build()
        assertEquals(long, tags(tag).title)
        assertEquals("Artist", tags(tag).artist)
        // 257 bytes: `00 00 01 01` is valid syncsafe too (129), but only the plain reading lands on a frame.
        val other = "y".repeat(256)
        val ambiguous = Id3Builder(4).frame("TIT2", textBody(other), plainSize = true).text("TPE1", "Artist").build()
        assertEquals(other, tags(ambiguous).title)
        assertEquals("Artist", tags(ambiguous).artist)
    }

    @Test
    fun paddingEndsFrames() {
        val tag = Id3Builder(3).text("TIT2", "T").build(padding = 512)
        assertEquals("T", tags(tag).title)
    }

    @Test
    fun consecutiveTagsFirstWins() {
        val first = Id3Builder(4).text("TIT2", "First").build(footer = true)
        val second = Id3Builder(3).text("TIT2", "Second").text("TALB", "Album").build()
        val t = tags(first + second)
        assertEquals("First", t.title)
        assertEquals("Album", t.album)
    }

    @Test
    fun unknownMajorVersionIsSkipped() {
        val unknown = latin1("ID3") + bytesOf(5, 0, 0) + syncsafe(20) + ByteArray(20) { 0x41 }
        val known = Id3Builder(3).text("TIT2", "Known").build()
        val result = readMp3(unknown + known + Mpeg.frames(3))
        assertEquals("Known", result.tags.title)
        assertEquals(Mpeg.SAMPLES * 3.0 / Mpeg.RATE, result.duration!!.inWholeMicroseconds / 1e6, 1e-3)
    }
}
