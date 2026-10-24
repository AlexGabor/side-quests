package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WavTest {

    private fun wav(vararg chunks: ByteArray) = readAs(AudioFormat.Wav, Wav.file(*chunks))

    @Test
    fun pcmDuration() {
        assertEquals(3.seconds, wav(Wav.fmt(16_000), Wav.chunk("data", ByteArray(48_000))).duration)
    }

    @Test
    fun fractionalDuration() {
        assertEquals(1500.milliseconds, wav(Wav.fmt(16_000), Wav.chunk("data", ByteArray(24_000))).duration)
    }

    @Test
    fun unfinishedDataSize() {
        val file = Wav.file(Wav.fmt(16_000), latin1("data") + le32(0xFFFFFFFFL) + ByteArray(32_000))
        assertEquals(2.seconds, readAs(AudioFormat.Wav, file).duration)
    }

    @Test
    fun dataSizePastTheEnd() {
        val file = Wav.file(Wav.fmt(16_000), latin1("data") + le32(1_000_000) + ByteArray(16_000))
        assertEquals(1.seconds, readAs(AudioFormat.Wav, file).duration)
    }

    @Test
    fun noFormatNoDuration() {
        assertNull(wav(Wav.chunk("data", ByteArray(100))).duration)
        assertNull(wav(Wav.fmt(0), Wav.chunk("data", ByteArray(100))).duration)
        assertNull(wav(Wav.fmt(8000)).duration)
    }

    @Test
    fun infoTags() {
        val result = wav(
            Wav.fmt(8000),
            Wav.info("INAM" to latin1("Title"), "IART" to latin1("Artist"), "IPRD" to latin1("Album"), "ICRD" to latin1("2019-01-01"), "ITRK" to latin1("4"), "IGNR" to latin1("Speech")),
            Wav.chunk("data", ByteArray(8000)),
        )
        assertEquals(
            AudioTags(title = "Title", album = "Album", artist = "Artist", genre = "Speech", year = 2019, trackNumber = 4),
            result.tags,
        )
    }

    @Test
    fun infoTrackForms() {
        val tags = wav(Wav.info("IPRT" to latin1("2/9"), "ITRK" to latin1("5"))).tags
        assertEquals(2, tags.trackNumber)
        assertEquals(9, tags.trackTotal)
    }

    @Test
    fun infoEncodings() {
        val tags = wav(Wav.info("INAM" to utf8("Ünïcødé ★"), "IART" to bytesOf(0x43, 0x61, 0x66, 0xE9, 0x20, 0x96))).tags
        assertEquals("Ünïcødé ★", tags.title)
        assertEquals("Café –", tags.artist)
    }

    @Test
    fun infoAfterData() {
        val result = wav(Wav.fmt(8000), Wav.chunk("data", ByteArray(8000)), Wav.info("INAM" to latin1("Late")))
        assertEquals("Late", result.tags.title)
    }

    @Test
    fun id3Chunk() {
        val id3 = Id3Builder(4).text("TIT2", "Id3").frame("APIC", apicBody("image/png", 3, PNG))
            .ctoc("toc", listOf("c1", "c2"), topLevel = true).chap("c1", 0, 1000, "One").chap("c2", 1000, 2000, "Two").build()
        val result = wav(Wav.fmt(8000), Wav.info("INAM" to latin1("Info"), "IART" to latin1("Artist")), Wav.chunk("data", ByteArray(16_000)), Wav.chunk("id3 ", id3))
        assertEquals("Id3", result.tags.title)
        assertEquals("Artist", result.tags.artist)
        assertEquals(Cover(PNG, "image/png"), result.cover)
        assertEquals(listOf(Chapter("One", 0.seconds, 1.seconds), Chapter("Two", 1.seconds, 2.seconds)), result.chapters)
    }

    @Test
    fun upperCaseId3Chunk() {
        val id3 = Id3Builder(3).text("TALB", "Album").build()
        assertEquals("Album", wav(Wav.chunk("ID3 ", id3)).tags.album)
    }

    @Test
    fun oddSizedChunk() {
        val result = wav(Wav.fmt(8000), Wav.chunk("junk", ByteArray(3)), Wav.chunk("data", ByteArray(4000)))
        assertEquals(500.milliseconds, result.duration)
    }

    @Test
    fun chunkPastTheEndStopsTheWalk() {
        val file = Wav.file(Wav.fmt(8000), latin1("LIST") + le32(1_000_000) + latin1("INFO") + Wav.chunk("INAM", latin1("X")))
        val result = readAs(AudioFormat.Wav, file)
        assertNull(result.tags.title)
        assertNull(result.duration)
    }

    @Test
    fun largeFileReadsLittle() {
        val head = Wav.file(Wav.fmt(176_400), Wav.info("INAM" to latin1("T")), Wav.chunk("junk", ByteArray(3000)))
        val dataHeader = latin1("data") + le32(500L * 1024 * 1024)
        val source = CountingSource(head + dataHeader, head.size + 8 + 500L * 1024 * 1024)
        val result = AudioMetadataReader.read(source)!!
        assertEquals("T", result.tags.title)
        assertTrue(result.duration!! > 2900.seconds)
        assertTrue(source.bytesRead < 64 * 1024, "read ${source.bytesRead} bytes")
    }
}
