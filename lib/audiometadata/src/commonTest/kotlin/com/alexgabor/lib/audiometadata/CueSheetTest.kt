package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class CueSheetTest {

    private fun parse(text: String) = assertNotNull(CueSheet.parse(utf8(text)))

    /** mm:ss:ff at 75 frames per second, as the parser computes it. */
    private fun cue(m: Long, s: Long, f: Long): Duration = (m * 60 + s).seconds + (f * 1_000_000_000 / 75).nanoseconds

    @Test
    fun singleFile() {
        val sheet = parse(
            """
            FILE "book.wav" WAVE
              TRACK 01 AUDIO
                TITLE "One"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Two"
                INDEX 01 01:30:37
            """.trimIndent(),
        )
        val split = cue(1, 30, 37)
        assertEquals(
            listOf(Chapter("One", Duration.ZERO, split), Chapter("Two", split, 3.minutes)),
            sheet.chapters("book.flac", 3.minutes),
        )
        // The only file entry is used whatever the name.
        assertEquals(2, sheet.chapters("other.mp3", null).size)
        assertEquals(2, sheet.chapters(null, null).size)
    }

    @Test
    fun severalFiles() {
        val sheet = parse(
            """
            PERFORMER "Author"
            TITLE "Book"
            FILE "part1.mp3" MP3
              TRACK 01 AUDIO
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                INDEX 01 05:00:00
            FILE "part2.mp3" MP3
              TRACK 03 AUDIO
                TITLE "Three"
                INDEX 01 00:00:00
            """.trimIndent(),
        )
        assertEquals("Book", sheet.title)
        assertEquals("Author", sheet.performer)
        assertEquals(listOf(Chapter("Three", Duration.ZERO, 2.minutes)), sheet.chapters("part2.mp3", 2.minutes))
        assertEquals(2, sheet.chapters("C:\\Books\\PART1.MP3", null).size)
        assertEquals(emptyList(), sheet.chapters("part3.mp3", null))
        assertEquals(emptyList(), sheet.chapters(null, null))
    }

    @Test
    fun exactNameWinsOverStem() {
        val sheet = parse(
            """
            FILE "a.wav" WAVE
              TRACK 01 AUDIO
                TITLE "Wav"
                INDEX 01 00:00:00
            FILE "a.flac" WAVE
              TRACK 02 AUDIO
                TITLE "Flac"
                INDEX 01 00:00:00
            """.trimIndent(),
        )
        assertEquals("Flac", sheet.chapters("a.flac", null).single().title)
        assertEquals("Wav", sheet.chapters("a.mp3", null).single().title)
    }

    @Test
    fun missingIndexOne() {
        val sheet = parse(
            """
            FILE "a.wav" WAVE
              TRACK 01 AUDIO
                INDEX 00 00:10:00
              TRACK 02 AUDIO
                TITLE "No index"
            """.trimIndent(),
        )
        assertEquals(listOf(10.seconds, null), sheet.files.single().tracks.map { it.start })
        assertEquals(listOf(Chapter(null, 10.seconds, 1.minutes)), sheet.chapters("a.wav", 1.minutes))
    }

    @Test
    fun indexOneWinsOverPregap() {
        val sheet = parse("TRACK 1 AUDIO\nINDEX 00 00:01:00\nINDEX 02 00:09:00\nINDEX 01 00:02:00")
        assertEquals(2.seconds, sheet.files.single().tracks.single().start)
        val noOne = parse("TRACK 1 AUDIO\nINDEX 03 00:09:00\nINDEX 02 00:05:00")
        assertEquals(5.seconds, noOne.files.single().tracks.single().start)
    }

    @Test
    fun tracksBeforeAnyFile() {
        val sheet = parse("TRACK 01 AUDIO\nINDEX 01 00:00:00")
        assertNull(sheet.files.single().name)
        assertEquals(1, sheet.chapters("anything.mp3", null).size)
    }

    @Test
    fun longMinutes() {
        val sheet = parse("TRACK 01 AUDIO\nINDEX 01 125:00:00")
        assertEquals(125.minutes, sheet.files.single().tracks.single().start)
    }

    @Test
    fun badTimesAreIgnored() {
        val sheet = parse("TRACK 01 AUDIO\nINDEX 01 00:60:00\nINDEX 01 1:2\nINDEX 01 00:00:75\nINDEX x 00:00:00")
        assertNull(sheet.files.single().tracks.single().start)
    }

    @Test
    fun quotingAndCase() {
        val sheet = parse("title \"Spaced   Title\"\rperformer Bare Word\rfile \"My Book.wav\" WAVE\rtrack 01 audio\r  title \"Unclosed\r  index 01 00:00:00\r")
        assertEquals("Spaced   Title", sheet.title)
        assertEquals("Bare", sheet.performer)
        assertEquals("My Book.wav", sheet.files.single().name)
        assertEquals("Unclosed", sheet.files.single().tracks.single().title)
    }

    @Test
    fun remAndUnknownCommandsAreIgnored() {
        val sheet = parse("REM TITLE \"Not this\"\nCATALOG 1234567890123\nTITLE \"This\"\nTRACK 01 AUDIO\nFLAGS DCP\nISRC X\nINDEX 01 00:00:00")
        assertEquals("This", sheet.title)
    }

    @Test
    fun trackTitleAndPerformer() {
        val sheet = parse("TITLE \"Book\"\nTRACK 01 AUDIO\nTITLE \"Ch\"\nPERFORMER \"Reader\"\nINDEX 01 00:00:00")
        assertEquals("Book", sheet.title)
        assertEquals(CueSheet.Track(1, "Ch", "Reader", Duration.ZERO), sheet.files.single().tracks.single())
    }

    @Test
    fun windows1252() {
        val bytes = latin1("TITLE \"Caf") + bytesOf(0xE9, 0x20, 0x96, 0x20) + latin1("Br") + bytesOf(0xFC) + latin1("cke\"\r\nTRACK 01 AUDIO\r\n")
        assertEquals("Café – Brücke", CueSheet.parse(bytes)?.title)
    }

    @Test
    fun utf8WithBom() {
        val bytes = bytesOf(0xEF, 0xBB, 0xBF) + utf8("TITLE \"Ünïcødé ★\"\nTRACK 01 AUDIO\n")
        assertEquals("Ünïcødé ★", CueSheet.parse(bytes)?.title)
    }

    @Test
    fun utf16() {
        val text = "TITLE \"Ünïcødé ★\"\r\nTRACK 01 AUDIO\r\n"
        assertEquals("Ünïcødé ★", CueSheet.parse(utf16(text, bigEndian = false))?.title)
        assertEquals("Ünïcødé ★", CueSheet.parse(utf16(text, bigEndian = true))?.title)
        assertEquals("Ünïcødé ★", CueSheet.parse(utf16(text, bigEndian = false, bom = false))?.title)
        assertEquals("Ünïcødé ★", CueSheet.parse(utf16(text, bigEndian = true, bom = false))?.title)
    }

    @Test
    fun notACueSheet() {
        assertNull(CueSheet.parse(bytesOf(0x89, 'P'.code, 'N'.code, 'G'.code, 0, 0, 0, 13) + ByteArray(100)))
        assertNull(CueSheet.parse(utf8("TITLE \"Just a title\"\nFILE \"x.wav\" WAVE\n")))
        assertNull(CueSheet.parse(ByteArray(0)))
    }

    @Test
    fun readsFromASource() {
        assertEquals("T", CueSheet.read(ByteArraySource(utf8("TITLE T\nTRACK 01 AUDIO")))?.title)
        assertNull(CueSheet.read(FailingSource))
    }

    @Test
    fun duplicateStartsKeepTheFirst() {
        val sheet = parse("TRACK 01 AUDIO\nTITLE A\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nTITLE B\nINDEX 01 00:00:00")
        assertEquals(listOf("A"), sheet.chapters(null, 1.minutes).map { it.title })
    }
}
