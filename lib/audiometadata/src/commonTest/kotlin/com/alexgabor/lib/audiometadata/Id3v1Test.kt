package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Id3v1Test {

    @Test
    fun version11() {
        val tags = readMp3(Mpeg.frames(3) + id3v1("Old", "Artist", "Album", "1999", track = 7, genre = 101)).tags
        assertEquals(
            AudioTags(title = "Old", artist = "Artist", album = "Album", year = 1999, trackNumber = 7, genre = "Speech"),
            tags,
        )
    }

    @Test
    fun version10HasNoTrack() {
        val tag = id3v1("Old").also { it[3 + 30 * 3 + 4 + 28] = 'x'.code.toByte(); it[3 + 30 * 3 + 4 + 29] = 9 }
        assertNull(readMp3(Mpeg.frames(3) + tag).tags.trackNumber)
    }

    @Test
    fun spacePaddedFieldsAndNoGenre() {
        val tags = readMp3(Mpeg.frames(3) + id3v1("Padded      ", genre = 255)).tags
        assertEquals("Padded", tags.title)
        assertNull(tags.genre)
    }

    @Test
    fun latin1OrUtf8Text() {
        val latin = id3v1().also { latin1("Café").copyInto(it, 3) }
        assertEquals("Café", readMp3(Mpeg.frames(1) + latin).tags.title)
        val utf = id3v1().also { utf8("Ünï ★").copyInto(it, 3) }
        assertEquals("Ünï ★", readMp3(Mpeg.frames(1) + utf).tags.title)
    }

    @Test
    fun id3v2TakesPrecedence() {
        val v2 = Id3Builder(3).text("TIT2", "New").build()
        val tags = readMp3(v2 + Mpeg.frames(3) + id3v1("Old", album = "Album")).tags
        assertEquals("New", tags.title)
        assertEquals("Album", tags.album)
    }

    @Test
    fun genreTable() {
        val names = (0..191).map { readMp3(Mpeg.frames(1) + id3v1(genre = it)).tags.genre }
        assertEquals("Blues", names[0])
        assertEquals("Hard Rock", names[79])
        assertEquals("Speech", names[101])
        assertEquals("Audiobook", names[183])
        assertEquals("Psybient", names[191])
        assertEquals(192, names.filterNotNull().distinct().size)
        assertNull(readMp3(Mpeg.frames(1) + id3v1(genre = 192)).tags.genre)
    }
}
