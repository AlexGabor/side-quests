package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Mp4TagsTest {

    private fun tags(vararg items: ByteArray, fullBox: Boolean = true) =
        readMp4(Mp4.ftyp() + Mp4.atom("moov", Mp4.mvhd(1000, 1000), Mp4.udta(*items, fullBox = fullBox)))

    @Test
    fun everyMappedItem() {
        val result = tags(
            Mp4.text("©nam", "T"), Mp4.text("©alb", "A"), Mp4.text("©ART", "R"), Mp4.text("aART", "B"),
            Mp4.text("©wrt", "C"), Mp4.text("©nrt", "N"), Mp4.text("©gen", "Audiobook"),
            Mp4.text("©day", "2021-05-01T00:00:00Z"), Mp4.number("trkn", 3, 12), Mp4.number("disk", 1, 2),
        )
        assertEquals(
            AudioTags(
                title = "T", album = "A", artist = "R", albumArtist = "B", composer = "C", narrator = "N",
                genre = "Audiobook", year = 2021, trackNumber = 3, trackTotal = 12, discNumber = 1, discTotal = 2,
            ),
            result.tags,
        )
    }

    @Test
    fun freeformNarrator() {
        val result = tags(Mp4.freeform("com.apple.iTunes", "NARRATOR", "Jane Doe"))
        assertEquals("Jane Doe", result.tags.narrator)
        assertEquals("John Roe", tags(Mp4.freeform("org.example", "Narrated By", "John Roe")).tags.narrator)
        assertNull(tags(Mp4.freeform("com.apple.iTunes", "MOOD", "Calm")).tags.narrator)
    }

    @Test
    fun narratorAtomWinsOverFreeform() {
        val result = tags(Mp4.freeform("com.apple.iTunes", "NARRATOR", "Freeform"), Mp4.text("©nrt", "Atom"))
        assertEquals("Atom", result.tags.narrator)
    }

    @Test
    fun composerIsNotTheNarrator() {
        val result = tags(Mp4.text("©wrt", "Composer"))
        assertEquals("Composer", result.tags.composer)
        assertNull(result.tags.narrator)
    }

    @Test
    fun numericGenre() {
        assertEquals("Speech", tags(Mp4.item("gnre", 0, u16(102))).tags.genre)
        assertEquals("Text", tags(Mp4.item("gnre", 0, u16(102)), Mp4.text("©gen", "Text")).tags.genre)
        assertNull(tags(Mp4.item("gnre", 0, u16(0))).tags.genre)
    }

    @Test
    fun utf16AndUtf8Text() {
        assertEquals("Ünïcødé ★", tags(Mp4.item("©nam", 2, utf16("Ünïcødé ★", bigEndian = true, bom = false))).tags.title)
        assertEquals("Ünïcødé ★", tags(Mp4.text("©nam", "Ünïcødé ★")).tags.title)
    }

    @Test
    fun trailingNulsAndBlanksAreTrimmed() {
        val result = tags(Mp4.item("©nam", 1, utf8("Title\u0000")), Mp4.text("©alb", "  "))
        assertEquals("Title", result.tags.title)
        assertNull(result.tags.album)
    }

    @Test
    fun zeroNumbersAreAbsent() {
        val result = tags(Mp4.number("trkn", 4, 0), Mp4.number("disk", 0, 0))
        assertEquals(4, result.tags.trackNumber)
        assertNull(result.tags.trackTotal)
        assertNull(result.tags.discNumber)
    }

    @Test
    fun pngAndJpegCovers() {
        assertEquals(Cover(PNG, "image/png"), tags(Mp4.item("covr", 14, PNG)).cover)
        assertEquals(Cover(JPEG, "image/jpeg"), tags(Mp4.item("covr", 13, JPEG)).cover)
    }

    @Test
    fun coverOfUnknownTypeIsSniffed() {
        assertEquals(Cover(PNG, "image/png"), tags(Mp4.item("covr", 0, PNG)).cover)
    }

    @Test
    fun firstImageWins() {
        val covr = Mp4.atom("covr", Mp4.atom("data", u32(13), u32(0), JPEG), Mp4.atom("data", u32(14), u32(0), PNG))
        assertEquals(Cover(JPEG, "image/jpeg"), tags(covr).cover)
    }

    @Test
    fun emptyCoverIsIgnored() {
        assertNull(tags(Mp4.item("covr", 13, ByteArray(0))).cover)
    }

    @Test
    fun quickTimeMeta() {
        assertEquals("T", tags(Mp4.text("©nam", "T"), fullBox = false).tags.title)
    }
}
