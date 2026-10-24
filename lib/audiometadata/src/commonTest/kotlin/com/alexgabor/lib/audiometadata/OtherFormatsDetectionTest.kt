package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OtherFormatsDetectionTest {

    private fun detect(bytes: ByteArray) = AudioMetadataReader.detect(ByteArraySource(bytes))

    private val flac = Flac.file(Flac.STREAMINFO to Flac.streamInfo(8000, 8000))
    private val vorbis = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(vorbisComments()), 8000)
    private val opus = Ogg.stream(Ogg.opusHead(312), Ogg.opusTags(vorbisComments()), 48312)
    private val wav = Wav.file(Wav.fmt(8000), Wav.chunk("data", ByteArray(800)))
    private val aac = AdtsFrames.frames(3, 20)

    @Test
    fun eachNewFormat() {
        assertEquals(AudioFormat.Flac, detect(flac))
        assertEquals(AudioFormat.Vorbis, detect(vorbis))
        assertEquals(AudioFormat.Opus, detect(opus))
        assertEquals(AudioFormat.Wav, detect(wav))
        assertEquals(AudioFormat.Aac, detect(aac))
    }

    @Test
    fun afterId3v2() {
        val id3 = Id3Builder(4).text("TIT2", "T").build()
        assertEquals(AudioFormat.Flac, detect(id3 + flac))
        assertEquals(AudioFormat.Aac, detect(id3 + aac))
        assertEquals(AudioFormat.Opus, detect(id3 + opus))
    }

    @Test
    fun otherOggCodecs() {
        assertNull(detect(Ogg.page(bytesOf(0x7F) + latin1("FLAC") + ByteArray(20), flags = 0x02)))
        assertNull(detect(Ogg.page(latin1("Speex   ") + ByteArray(72), flags = 0x02)))
        // Not a beginning-of-stream page.
        assertNull(detect(Ogg.page(Ogg.vorbisId(8000))))
    }

    @Test
    fun riffThatIsNotWave() {
        assertNull(detect(latin1("RIFF") + le32(4) + latin1("AVI ") + ByteArray(40)))
    }

    @Test
    fun loneAdtsSync() {
        // A plausible header whose frame ends on zeros instead of another sync.
        assertNull(detect(AdtsFrames.frame(20) + ByteArray(40)))
        // A single frame that ends exactly at the end of the source is fine.
        assertEquals(AudioFormat.Aac, detect(AdtsFrames.frame(20)))
    }

    @Test
    fun adtsIsNotMp3NorTheReverse() {
        assertEquals(AudioFormat.Mp3, detect(Mpeg.frames(3)))
        assertEquals(AudioFormat.Aac, detect(AdtsFrames.frames(3, 400, AdtsFrames.RATE_44100)))
    }

    @Test
    fun invalidAdtsFields() {
        val badRate = AdtsFrames.frames(3, 20).also { it[2] = (0x40 or (13 shl 2)).toByte() }
        assertNull(detect(badRate))
        val tooShort = AdtsFrames.frame(20).also { it[4] = 0; it[5] = (3 shl 5).toByte() }
        assertNull(detect(tooShort + AdtsFrames.frame(20)))
    }
}
