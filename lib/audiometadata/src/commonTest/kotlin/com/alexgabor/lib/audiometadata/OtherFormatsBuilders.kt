package com.alexgabor.lib.audiometadata

import com.alexgabor.lib.audiometadata.ogg.OggCrc

/** Byte-level builders for FLAC, Ogg, WAV and ADTS inputs ffmpeg can't produce. `concat`, `u64` and `SparseSource` are in `Mp4Builder`. */

fun le16(v: Int) = bytesOf(v, v shr 8)

fun le32(v: Long) = bytesOf(v.toInt(), (v shr 8).toInt(), (v shr 16).toInt(), (v shr 24).toInt())

fun le64(v: Long) = le32(v and 0xFFFFFFFFL) + le32(v ushr 32)

/** A Vorbis comment list: vendor, count, then `KEY=value` entries. [count] overrides the real count. */
fun vorbisComments(vararg entries: String, vendor: String = "test", count: Long? = null): ByteArray =
    le32(utf8(vendor).size.toLong()) + utf8(vendor) + le32(count ?: entries.size.toLong()) +
        concat(entries.map { le32(utf8(it).size.toLong()) + utf8(it) })

/** A FLAC picture block body, as PICTURE and `METADATA_BLOCK_PICTURE` hold it. */
fun flacPicture(type: Int, mime: String, image: ByteArray, declaredLength: Long? = null): ByteArray =
    u32(type.toLong()) + u32(mime.length.toLong()) + latin1(mime) + u32(4) + utf8("desc") +
        u32(8) + u32(8) + u32(24) + u32(0) + u32(declaredLength ?: image.size.toLong()) + image

fun base64(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val out = StringBuilder()
    for (i in bytes.indices step 3) {
        val n = (bytes[i].toInt() and 0xFF shl 16) or
            ((bytes.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF shl 8) or
            ((bytes.getOrNull(i + 2)?.toInt() ?: 0) and 0xFF)
        out.append(alphabet[n shr 18 and 63]).append(alphabet[n shr 12 and 63])
        out.append(if (i + 1 < bytes.size) alphabet[n shr 6 and 63] else '=')
        out.append(if (i + 2 < bytes.size) alphabet[n and 63] else '=')
    }
    return out.toString()
}

object Flac {
    const val STREAMINFO = 0
    const val PADDING = 1
    const val VORBIS_COMMENT = 4
    const val CUESHEET = 5
    const val PICTURE = 6

    /** STREAMINFO: block sizes, frame sizes, then rate (20 bits), channels − 1, bits − 1, samples (36 bits), MD5. */
    fun streamInfo(rate: Int, samples: Long): ByteArray {
        val b = ByteArray(34)
        b[0] = 0x10; b[2] = 0x10
        b[10] = (rate shr 12).toByte()
        b[11] = (rate shr 4).toByte()
        // Mono, 16 bits per sample: channels − 1 = 0, bits − 1 = 15.
        b[12] = ((rate and 0x0F) shl 4).toByte()
        b[13] = (0xF0 or ((samples shr 32).toInt() and 0x0F)).toByte()
        u32(samples and 0xFFFFFFFFL).copyInto(b, 14)
        return b
    }

    fun block(type: Int, body: ByteArray, last: Boolean = false, declaredSize: Int? = null): ByteArray {
        val size = declaredSize ?: body.size
        return bytesOf((if (last) 0x80 else 0) or type, size shr 16, size shr 8, size) + body
    }

    /** `fLaC`, then the blocks, the last one flagged, then a few bytes standing in for audio frames. */
    fun file(vararg blocks: Pair<Int, ByteArray>): ByteArray =
        latin1("fLaC") + concat(blocks.mapIndexed { i, (type, body) -> block(type, body, last = i == blocks.lastIndex) }) +
            bytesOf(0xFF, 0xF8, 0, 0)

    class Track(val offset: Long, val number: Int, val indexes: List<Pair<Int, Long>> = listOf(1 to 0L))

    fun cueSheet(tracks: List<Track>, cd: Boolean = false): ByteArray =
        ByteArray(128) + ByteArray(8) + bytesOf(if (cd) 0x80 else 0) + ByteArray(258) + bytesOf(tracks.size) +
            concat(tracks.map { t ->
                u64(t.offset) + bytesOf(t.number) + ByteArray(12) + bytesOf(0) + ByteArray(13) + bytesOf(t.indexes.size) +
                    concat(t.indexes.map { (n, offset) -> u64(offset) + bytesOf(n) + ByteArray(3) })
            })
}

object Ogg {
    fun vorbisId(rate: Long) = bytesOf(1) + latin1("vorbis") + le32(0) + bytesOf(1) + le32(rate) + ByteArray(13)

    fun vorbisComments(comments: ByteArray) = bytesOf(3) + latin1("vorbis") + comments + bytesOf(1)

    fun opusHead(preSkip: Int) = latin1("OpusHead") + bytesOf(1, 1) + le16(preSkip) + le32(48000) + bytesOf(0, 0, 0)

    fun opusTags(comments: ByteArray) = latin1("OpusTags") + comments

    /**
     * One page, with its CRC. [lacing] defaults to one segment run for [body] as a single complete
     * packet.
     */
    fun page(
        body: ByteArray,
        serial: Long = 1,
        sequence: Long = 0,
        granule: Long = 0,
        flags: Int = 0,
        lacing: IntArray = lacing(body.size),
        badCrc: Boolean = false,
    ): ByteArray {
        val header = latin1("OggS") + bytesOf(0, flags) + le64(granule) + le32(serial) + le32(sequence) + le32(0) +
            bytesOf(lacing.size) + ByteArray(lacing.size) { lacing[it].toByte() }
        val page = header + body
        val crc = OggCrc.of(page).toLong() and 0xFFFFFFFFL
        le32(if (badCrc) crc xor 1 else crc).copyInto(page, 22)
        return page
    }

    fun lacing(size: Int): IntArray = IntArray(size / 255 + 1) { if (it < size / 255) 255 else size % 255 }

    /**
     * [packet] split over pages of at most [segmentsPerPage] segments. Every page but the last has
     * granule −1 and every page but the first the continuation flag.
     */
    fun pages(packet: ByteArray, serial: Long = 1, firstSequence: Long = 0, granule: Long = 0, flags: Int = 0, segmentsPerPage: Int = 255): List<ByteArray> {
        val lacing = lacing(packet.size)
        val out = mutableListOf<ByteArray>()
        var segment = 0
        var offset = 0
        while (segment < lacing.size) {
            val laces = lacing.copyOfRange(segment, minOf(lacing.size, segment + segmentsPerPage))
            val size = laces.sum()
            val last = segment + laces.size == lacing.size
            out += page(
                packet.copyOfRange(offset, offset + size),
                serial = serial,
                sequence = firstSequence + out.size,
                granule = if (last) granule else -1,
                flags = (if (segment > 0) 0x01 else 0) or (if (segment == 0) flags else 0),
                lacing = laces,
            )
            segment += laces.size
            offset += size
        }
        return out
    }

    /** Identification page, comment pages, then one audio page per [audio] entry, the last with [granule]. */
    fun stream(id: ByteArray, comments: ByteArray, granule: Long, serial: Long = 1, segmentsPerPage: Int = 255, audio: List<ByteArray> = listOf(ByteArray(40))): ByteArray {
        val pages = mutableListOf(page(id, serial, 0, 0, flags = 0x02))
        pages += pages(comments, serial, 1, segmentsPerPage = segmentsPerPage)
        audio.forEachIndexed { i, body ->
            val last = i == audio.lastIndex
            pages += page(body, serial, pages.size.toLong(), granule = if (last) granule else granule / 2, flags = if (last) 0x04 else 0)
        }
        return concat(pages)
    }
}

object Wav {
    fun chunk(id: String, body: ByteArray, declaredSize: Long? = null): ByteArray =
        latin1(id) + le32(declaredSize ?: body.size.toLong()) + body + if (body.size % 2 == 1) bytesOf(0) else ByteArray(0)

    /** PCM `fmt `: format 1, mono, [byteRate] bytes per second at 8 bits. */
    fun fmt(byteRate: Long, format: Int = 1) =
        chunk("fmt ", le16(format) + le16(1) + le32(byteRate) + le32(byteRate) + le16(1) + le16(8))

    fun info(vararg values: Pair<String, ByteArray>) = chunk(
        "LIST",
        latin1("INFO") + concat(values.map { (id, value) -> chunk(id, value + bytesOf(0)) }),
    )

    fun file(vararg chunks: ByteArray): ByteArray {
        val body = latin1("WAVE") + concat(chunks.toList())
        return latin1("RIFF") + le32(body.size.toLong()) + body
    }
}

object AdtsFrames {
    const val RATE_8000 = 11
    const val RATE_44100 = 4

    /** One frame of [length] bytes (header included), [blocks] raw blocks, no CRC unless [crc]. */
    fun frame(length: Int, rateIndex: Int = RATE_8000, blocks: Int = 1, crc: Boolean = false): ByteArray {
        val b = ByteArray(length)
        b[0] = 0xFF.toByte()
        b[1] = (if (crc) 0xF0 else 0xF1).toByte()
        b[2] = (0x40 or (rateIndex shl 2)).toByte()
        b[3] = (0x40 or (length shr 11 and 0x03)).toByte()
        b[4] = (length shr 3).toByte()
        b[5] = ((length and 0x07) shl 5 or 0x1F).toByte()
        b[6] = (0xFC or (blocks - 1)).toByte()
        return b
    }

    fun frames(count: Int, length: Int, rateIndex: Int = RATE_8000): ByteArray {
        val one = frame(length, rateIndex)
        return ByteArray(one.size * count) { one[it % one.size] }
    }
}

fun readAs(format: AudioFormat, bytes: ByteArray): AudioMetadata {
    val result = read(bytes) ?: error("not detected")
    check(result.format == format) { "detected ${result.format}, not $format" }
    return result
}
