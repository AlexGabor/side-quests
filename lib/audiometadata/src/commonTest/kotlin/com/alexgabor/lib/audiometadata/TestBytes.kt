package com.alexgabor.lib.audiometadata

/** Byte-level builders for inputs ffmpeg can't produce. */

fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

fun latin1(s: String) = ByteArray(s.length) { s[it].code.toByte() }

fun utf8(s: String) = s.encodeToByteArray()

fun utf16(s: String, bigEndian: Boolean, bom: Boolean = true): ByteArray {
    val out = ArrayList<Byte>()
    if (bom) out += if (bigEndian) listOf(0xFE.toByte(), 0xFF.toByte()) else listOf(0xFF.toByte(), 0xFE.toByte())
    for (c in s) {
        val hi = (c.code shr 8).toByte()
        val lo = c.code.toByte()
        if (bigEndian) { out += hi; out += lo } else { out += lo; out += hi }
    }
    return out.toByteArray()
}

fun u32(v: Long) = bytesOf((v shr 24).toInt(), (v shr 16).toInt(), (v shr 8).toInt(), v.toInt())

fun syncsafe(v: Int) = bytesOf(v shr 21 and 0x7F, v shr 14 and 0x7F, v shr 7 and 0x7F, v and 0x7F)

/** Inserts `00` after every `FF` that is followed by `00`, by `111xxxxx`, or by nothing. */
fun unsynchronise(data: ByteArray): ByteArray {
    val out = ArrayList<Byte>(data.size + 16)
    for (i in data.indices) {
        out += data[i]
        if (data[i] == 0xFF.toByte()) {
            val next = data.getOrNull(i + 1)?.toInt()?.and(0xFF)
            if (next == null || next == 0 || next and 0xE0 == 0xE0) out += 0
        }
    }
    return out.toByteArray()
}

/** A text frame body in [encoding]: 0 Latin-1, 1 UTF-16 LE with BOM, 2 UTF-16BE, 3 UTF-8. */
fun textBody(value: String, encoding: Int = 3): ByteArray = bytesOf(encoding) + encode(value, encoding)

fun encode(value: String, encoding: Int): ByteArray = when (encoding) {
    0 -> latin1(value)
    1 -> utf16(value, bigEndian = false)
    2 -> utf16(value, bigEndian = true, bom = false)
    else -> utf8(value)
}

fun terminator(encoding: Int) = if (encoding == 1 || encoding == 2) bytesOf(0, 0) else bytesOf(0)

fun apicBody(mime: String, type: Int, image: ByteArray, description: String = "", encoding: Int = 0) =
    bytesOf(encoding) + latin1(mime) + bytesOf(0, type) + encode(description, encoding) + terminator(encoding) + image

fun picBody(format: String, type: Int, image: ByteArray) = bytesOf(0) + latin1(format) + bytesOf(type, 0) + image

/** A minimal JPEG-looking payload whose bytes need unsynchronising. */
val JPEG = bytesOf(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0xFF, 0x00, 0xFF, 0xFF, 0xE1, 0x42, 0xFF)
val PNG = bytesOf(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

/** One ID3v2 tag of version 2.[major]. */
class Id3Builder(private val major: Int) {
    private val frames = mutableListOf<ByteArray>()

    /** [format] is the 2.3/2.4 format-flags byte; [plainSize] writes a 2.4 size as a plain integer. */
    fun frame(id: String, body: ByteArray, format: Int = 0, plainSize: Boolean = false): Id3Builder {
        frames += frameBytes(id, body, format, plainSize)
        return this
    }

    fun text(id: String, value: String, encoding: Int = if (major == 4) 3 else 0) = frame(id, textBody(value, encoding))

    fun raw(bytes: ByteArray): Id3Builder {
        frames += bytes
        return this
    }

    fun frameBytes(id: String, body: ByteArray, format: Int = 0, plainSize: Boolean = false): ByteArray = when (major) {
        2 -> latin1(id) + bytesOf(body.size shr 16, body.size shr 8, body.size) + body
        3 -> latin1(id) + u32(body.size.toLong()) + bytesOf(0, format) + body
        else -> latin1(id) + (if (plainSize) u32(body.size.toLong()) else syncsafe(body.size)) + bytesOf(0, format) + body
    }

    fun chap(id: String, startMs: Long, endMs: Long, title: String? = null): Id3Builder {
        val sub = title?.let { frameBytes("TIT2", textBody(it)) } ?: ByteArray(0)
        return frame("CHAP", latin1(id) + bytesOf(0) + u32(startMs) + u32(endMs) + u32(0xFFFFFFFF) + u32(0xFFFFFFFF) + sub)
    }

    fun ctoc(id: String, children: List<String>, title: String? = null, topLevel: Boolean = false, ordered: Boolean = true): Id3Builder {
        val flags = (if (topLevel) 2 else 0) or (if (ordered) 1 else 0)
        val entries = children.fold(ByteArray(0)) { acc, child -> acc + latin1(child) + bytesOf(0) }
        val sub = title?.let { frameBytes("TIT2", textBody(it)) } ?: ByteArray(0)
        return frame("CTOC", latin1(id) + bytesOf(0, flags, children.size) + entries + sub)
    }

    /**
     * The tag. [unsync] unsynchronises the whole body (2.2/2.3) and sets the flag. [extendedHeader]
     * adds a minimal one. [declaredSize] overrides the header's size, e.g. to claim more than is there.
     */
    fun build(
        unsync: Boolean = false,
        extendedHeader: Boolean = false,
        padding: Int = 0,
        declaredSize: Int? = null,
        footer: Boolean = false,
    ): ByteArray {
        var body = ByteArray(0)
        if (extendedHeader) {
            body += if (major == 3) u32(6) + bytesOf(0, 0, 0, 0, 0, 0) else syncsafe(6) + bytesOf(1, 0)
        }
        frames.forEach { body += it }
        body += ByteArray(padding)
        if (unsync && major < 4) body = unsynchronise(body)
        val flags = (if (unsync) 0x80 else 0) or (if (extendedHeader) 0x40 else 0) or (if (footer) 0x10 else 0)
        val size = declaredSize ?: body.size
        val header = latin1("ID3") + bytesOf(major, 0, flags) + syncsafe(size)
        val foot = if (footer) latin1("3DI") + bytesOf(major, 0, flags) + syncsafe(size) else ByteArray(0)
        return header + body + foot
    }
}

fun id3v1(
    title: String = "",
    artist: String = "",
    album: String = "",
    year: String = "",
    track: Int? = null,
    genre: Int = 255,
): ByteArray {
    fun field(s: String, n: Int) = latin1(s).copyOf(n)
    val comment = ByteArray(30)
    if (track != null) comment[29] = track.toByte()
    return latin1("TAG") + field(title, 30) + field(artist, 30) + field(album, 30) + field(year, 4) + comment + bytesOf(genre)
}

/** MPEG audio frames: by default MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, stereo (417 bytes each). */
object Mpeg {
    val HEADER = bytesOf(0xFF, 0xFB, 0x90, 0x00)
    const val FRAME = 417
    const val SAMPLES = 1152
    const val RATE = 44100

    fun frame(header: ByteArray = HEADER, length: Int = FRAME) = header + ByteArray(length - header.size)

    fun frames(count: Int, header: ByteArray = HEADER, length: Int = FRAME): ByteArray {
        val one = frame(header, length)
        return ByteArray(one.size * count) { one[it % one.size] }
    }

    /** A first frame carrying a Xing (or Info) header with a frame count. Stereo MPEG-1: offset 36. */
    fun xing(frames: Long, tag: String = "Xing"): ByteArray {
        val f = frame()
        (latin1(tag) + u32(0x1) + u32(frames)).copyInto(f, 36)
        return f
    }

    /** A first frame carrying a VBRI header 32 bytes after the frame header. */
    fun vbri(frames: Long): ByteArray {
        val f = frame()
        (latin1("VBRI") + bytesOf(0, 1, 0, 0, 0, 50) + u32(123456) + u32(frames)).copyInto(f, 36)
        return f
    }
}

/** Counts the bytes handed out; [virtualSize] pads [prefix] with zeros that are never stored. */
class CountingSource(private val prefix: ByteArray, private val virtualSize: Long = prefix.size.toLong()) : ByteSource {
    var bytesRead = 0L
        private set

    override val size: Long get() = virtualSize

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= virtualSize || length <= 0) return ByteArray(0)
        val n = minOf(length.toLong(), virtualSize - position).toInt()
        val out = ByteArray(n)
        if (position < prefix.size) {
            val m = minOf(n, prefix.size - position.toInt())
            prefix.copyInto(out, 0, position.toInt(), position.toInt() + m)
        }
        bytesRead += n
        return out
    }
}

object FailingSource : ByteSource {
    override val size: Long get() = 1_000_000
    override fun read(position: Long, length: Int): ByteArray = throw IllegalStateException("I/O error")
}

fun read(bytes: ByteArray): AudioMetadata? = AudioMetadataReader.read(ByteArraySource(bytes))

fun readMp3(bytes: ByteArray): AudioMetadata = read(bytes) ?: error("not detected as MP3")
