package com.alexgabor.lib.audiometadata

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class OtherFormatsMalformedTest {

    private val flac = Flac.file(
        Flac.STREAMINFO to Flac.streamInfo(8000, 24000),
        Flac.VORBIS_COMMENT to vorbisComments("TITLE=T", "ALBUM=A", "ARTIST=R", "CHAPTER1=00:00:01.000"),
        Flac.PICTURE to flacPicture(3, "image/png", PNG),
        Flac.CUESHEET to Flac.cueSheet(listOf(Flac.Track(0, 1), Flac.Track(8000, 2), Flac.Track(24000, 255, emptyList()))),
    )
    private val opus = Ogg.stream(
        Ogg.opusHead(312),
        Ogg.opusTags(vorbisComments("TITLE=T", "ALBUM=A", "METADATA_BLOCK_PICTURE=" + base64(flacPicture(3, "image/png", PNG)), "CHAPTER1=00:00:01.000")),
        48312,
        segmentsPerPage = 1,
    )
    private val vorbis = Ogg.stream(Ogg.vorbisId(8000), Ogg.vorbisComments(vorbisComments("TITLE=T")), 8000)
    private val wav = Wav.file(
        Wav.fmt(8000),
        Wav.info("INAM" to latin1("T"), "IART" to latin1("R")),
        Wav.chunk("data", ByteArray(800)),
        Wav.chunk("id3 ", Id3Builder(4).text("TALB", "A").chap("c", 0, 100, "C").build()),
    )
    private val aac = AdtsFrames.frames(20, 30)

    private val all = listOf(flac, opus, vorbis, wav, aac)

    @Test
    fun truncatedFlacComment() {
        // Cut partway through the third comment: STREAMINFO and the first two comments survive.
        val cut = flac.copyOf(4 + 4 + 34 + 4 + (4 + 4) + 4 + (4 + 7) + (4 + 7) + 6)
        val result = readAs(AudioFormat.Flac, cut)
        assertEquals(3.seconds, result.duration)
        assertEquals("T", result.tags.title)
        assertEquals("A", result.tags.album)
        assertNull(result.tags.artist)
    }

    @Test
    fun truncatedOpusComment() {
        // Cut after the ALBUM entry, partway through the picture: the comments before it are kept.
        val packetStart = opus.indexOfSequence(latin1("OpusTags"))
        val cut = opus.copyOf(packetStart + 8 + 12 + 11 + 11 + 20)
        val result = readAs(AudioFormat.Opus, cut)
        assertEquals("T", result.tags.title)
        assertEquals("A", result.tags.album)
        assertNull(result.duration)
    }

    @Test
    fun hostilePictureLength() {
        val file = Flac.file(Flac.STREAMINFO to Flac.streamInfo(8000, 8000), Flac.PICTURE to flacPicture(3, "image/png", PNG, declaredLength = 0xFFFFFFF0L))
        val result = readAs(AudioFormat.Flac, file)
        assertEquals(1.seconds, result.duration)
        // The picture's length is bounded by its block, not by the file.
        assertEquals(Cover(PNG, "image/png"), result.cover)
    }

    @Test
    fun hostileBlockSize() {
        val file = latin1("fLaC") + Flac.block(Flac.STREAMINFO, Flac.streamInfo(8000, 8000)) +
            Flac.block(Flac.VORBIS_COMMENT, vorbisComments("TITLE=T"), last = true, declaredSize = 0xFFFFFF)
        val result = readAs(AudioFormat.Flac, file)
        assertEquals("T", result.tags.title)
        assertEquals(1.seconds, result.duration)
    }

    @Test
    fun hostileWavChunkSize() {
        val file = Wav.file(Wav.fmt(8000), Wav.chunk("data", ByteArray(8000)), latin1("id3 ") + le32(0xFFFFFFF0L) + ByteArray(10))
        assertEquals(1.seconds, readAs(AudioFormat.Wav, file).duration)
    }

    @Test
    fun hostileAdtsFrameLength() {
        // The second frame claims 8191 bytes, far past the end.
        val file = AdtsFrames.frame(30) + AdtsFrames.frame(8191).copyOf(30)
        val result = readAs(AudioFormat.Aac, file)
        assertEquals((2 * 1024 / 8000.0).seconds, result.duration)
    }

    @Test
    fun hostileOggSegmentTable() {
        // A second page whose lacing table claims 64 KB that isn't there.
        val file = Ogg.page(Ogg.opusHead(0), flags = 0x02) + Ogg.page(Ogg.opusTags(vorbisComments("TITLE=T")), sequence = 1, lacing = IntArray(255) { 255 })
        val result = readAs(AudioFormat.Opus, file)
        assertEquals("T", result.tags.title)
        // A first page whose lacing doesn't fit the detection probe isn't recognised, rather than misread.
        assertNull(read(Ogg.page(Ogg.opusHead(0), flags = 0x02, lacing = IntArray(255) { 255 })))
    }

    @Test
    fun failingSourceForEachFormat() {
        for (bytes in all) {
            val source = object : ByteSource {
                var reads = 0
                override val size = bytes.size.toLong()
                override fun read(position: Long, length: Int): ByteArray {
                    // Enough to detect the format, then every read fails.
                    if (reads++ > 2) throw IllegalStateException("I/O error")
                    return ByteArraySource(bytes).read(position, length)
                }
            }
            AudioMetadataReader.read(source)
        }
    }

    @Test
    fun everyTruncation() {
        for (bytes in all) {
            for (n in 0..bytes.size step maxOf(1, bytes.size / 400)) {
                read(bytes.copyOf(n))
                AudioMetadataReader.detect(ByteArraySource(bytes.copyOf(n)))
            }
        }
    }

    @Test
    fun randomCorruption() {
        val random = Random(6)
        for (bytes in all) {
            repeat(300) {
                val copy = bytes.copyOf()
                repeat(1 + random.nextInt(8)) { copy[random.nextInt(copy.size)] = random.nextInt(256).toByte() }
                read(copy)
            }
        }
    }

    @Test
    fun cueSheetFuzz() {
        val random = Random(7)
        val sheet = utf8("TITLE \"T\"\nFILE \"a.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nINDEX 01 99:59:74\n")
        repeat(500) {
            val copy = sheet.copyOf()
            repeat(1 + random.nextInt(6)) { copy[random.nextInt(copy.size)] = random.nextInt(256).toByte() }
            CueSheet.parse(copy)?.chapters("a.wav", null)
        }
        repeat(50) { CueSheet.parse(random.nextBytes(random.nextInt(2000))) }
    }

    private fun ByteArray.indexOfSequence(s: ByteArray): Int =
        (0..size - s.size).first { i -> s.indices.all { this[i + it] == s[it] } }
}
