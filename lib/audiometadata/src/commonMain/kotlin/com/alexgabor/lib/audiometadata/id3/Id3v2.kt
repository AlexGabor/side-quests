package com.alexgabor.lib.audiometadata.id3

import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.syncsafe
import com.alexgabor.lib.audiometadata.u24
import com.alexgabor.lib.audiometadata.u32
import com.alexgabor.lib.audiometadata.u8

/** One ID3v2 frame, with unsynchronisation and frame-level extras already removed. */
internal class Id3Frame(val id: String, val data: ByteArray)

/**
 * The structure of ID3v2.2, 2.3 and 2.4 tags: headers, extended headers, unsynchronisation and frame
 * flags. What the frames mean is up to [Id3Frames].
 */
internal object Id3v2 {

    class Tags(val count: Int, val end: Long)

    /** Where the run of ID3v2 tags at the start of the file ends, without reading their bodies. */
    fun skipTags(reader: Reader): Tags {
        var position = 0L
        var count = 0
        while (count < MAX_TAGS) {
            val header = header(reader, position) ?: break
            position += header.totalSize
            count++
        }
        return Tags(count, position)
    }

    /**
     * Reads each tag in the leading run, calling [onTag] with its major version and frames. A tag is
     * passed on with the frames read so far even when it is cut short.
     */
    fun readTags(reader: Reader, onTag: (major: Int, frames: List<Id3Frame>) -> Unit): Long {
        var position = 0L
        var count = 0
        while (count < MAX_TAGS) {
            val header = header(reader, position) ?: break
            if (header.major in 2..4) {
                val frames = mutableListOf<Id3Frame>()
                try {
                    readTag(reader, position, header, frames)
                } catch (_: Exception) {
                    // A damaged tag keeps the frames before the damage.
                }
                onTag(header.major, frames)
            }
            position += header.totalSize
            count++
        }
        return position
    }

    /**
     * Frames in `data[start until end]`, for a tag body or the sub-frames of `CHAP` and `CTOC`.
     * Stops at padding, at an invalid frame ID, or at a frame that runs past [end].
     */
    fun frames(data: ByteArray, start: Int, end: Int, major: Int, tagUnsynchronised: Boolean, into: MutableList<Id3Frame>) {
        val headerSize = if (major == 2) 6 else 10
        val idLength = if (major == 2) 3 else 4
        var p = start
        while (p + headerSize <= end) {
            if (data[p].toInt() == 0) return
            if (!isFrameId(data, p, idLength)) return
            val id = data.ascii(p, idLength)
            val size = when (major) {
                2 -> data.u24(p + 3).toLong()
                3 -> data.u32(p + 4)
                else -> frameSizeV24(data, p, end)
            }
            val dataStart = p + headerSize
            if (size < 0 || dataStart + size > end) return
            val dataEnd = (dataStart + size).toInt()
            frameData(data, p, dataStart, dataEnd, major, tagUnsynchronised)?.let { into += Id3Frame(id.mapV22(major), it) }
            p = dataEnd
        }
    }

    private class Header(val major: Int, val flags: Int, val size: Long) {
        val totalSize: Long get() = HEADER_SIZE + size + if (major == 4 && flags and FLAG_FOOTER != 0) HEADER_SIZE else 0
    }

    private fun header(reader: Reader, position: Long): Header? {
        val bytes = reader.bytes(position, HEADER_SIZE)
        if (bytes.size < HEADER_SIZE || !bytes.startsWith("ID3")) return null
        val major = bytes.u8(3)
        if (major == 0xFF || bytes.u8(4) == 0xFF) return null
        if ((6 until 10).any { bytes.u8(it) and 0x80 != 0 }) return null
        return Header(major, bytes.u8(5), bytes.syncsafe(6))
    }

    private fun readTag(reader: Reader, position: Long, header: Header, into: MutableList<Id3Frame>) {
        val length = minOf(header.size, MAX_TAG_SIZE).toInt()
        var body = reader.bytes(position + HEADER_SIZE, length)
        val unsynchronised = header.flags and FLAG_UNSYNC != 0
        // Before 2.4, unsynchronisation covers the whole tag, extended header included.
        if (unsynchronised && header.major < 4) body = resync(body, 0, body.size)
        // Version 2.2 used this bit for compression, with no scheme ever defined.
        if (header.major == 2 && header.flags and FLAG_EXTENDED != 0) return
        var start = 0
        if (header.flags and FLAG_EXTENDED != 0) {
            if (body.size < 4) return
            start = when (header.major) {
                3 -> 4 + body.u32(0)
                else -> body.syncsafe(0)
            }.coerceAtMost(body.size.toLong()).toInt()
        }
        frames(body, start, body.size, header.major, unsynchronised && header.major == 4, into)
    }

    /** Frame body without its 2.3/2.4 extras, or `null` when it is compressed or encrypted. */
    private fun frameData(data: ByteArray, header: Int, start: Int, end: Int, major: Int, tagUnsync: Boolean): ByteArray? {
        if (major == 2) return data.copyOfRange(start, end)
        val format = data.u8(header + 9)
        var p = start
        if (major == 3) {
            if (format and 0x80 != 0 || format and 0x40 != 0) return null
            if (format and 0x20 != 0) p += 1
            return if (p <= end) data.copyOfRange(p, end) else null
        }
        if (format and 0x08 != 0 || format and 0x04 != 0) return null
        if (format and 0x40 != 0) p += 1
        if (format and 0x01 != 0) p += 4
        if (p > end) return null
        return if (format and 0x02 != 0 || tagUnsync) resync(data, p, end) else data.copyOfRange(p, end)
    }

    /**
     * A 2.4 frame size. Some writers (notably old iTunes) store plain integers there instead of syncsafe
     * ones, so when the two readings differ, the one that lands on the next frame wins.
     */
    private fun frameSizeV24(data: ByteArray, p: Int, end: Int): Long {
        val plain = data.u32(p + 4)
        if (plain and 0x80808080L != 0L) return plain
        val syncsafe = data.syncsafe(p + 4)
        if (plain == syncsafe) return syncsafe
        val next = p + 10
        return if (!landsOnFrame(data, next + syncsafe, end) && landsOnFrame(data, next + plain, end)) plain else syncsafe
    }

    private fun landsOnFrame(data: ByteArray, position: Long, end: Int): Boolean = when {
        position == end.toLong() -> true
        position > end -> false
        position + 10 > end -> data[position.toInt()].toInt() == 0
        else -> data[position.toInt()].toInt() == 0 || isFrameId(data, position.toInt(), 4)
    }

    private fun isFrameId(data: ByteArray, p: Int, length: Int): Boolean {
        if (p + length > data.size) return false
        for (i in p until p + length) {
            val c = data[i].toInt().toChar()
            if (c !in 'A'..'Z' && c !in '0'..'9') return false
        }
        return true
    }

    /** Undoes unsynchronisation: every `FF 00` becomes `FF`. */
    internal fun resync(data: ByteArray, start: Int, end: Int): ByteArray {
        val out = ByteArray(end - start)
        var n = 0
        var i = start
        while (i < end) {
            val b = data[i]
            out[n++] = b
            i += if (b == 0xFF.toByte() && i + 1 < end && data[i + 1].toInt() == 0) 2 else 1
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    private fun ByteArray.ascii(p: Int, length: Int) = CharArray(length) { this[p + it].toInt().toChar() }.concatToString()

    /** 2.2's three-letter IDs, as their 2.3 equivalents. `PIC` stays: its body differs from `APIC`'s. */
    private fun String.mapV22(major: Int): String = if (major != 2) this else V22[this] ?: this

    private val V22 = mapOf(
        "TT2" to "TIT2", "TAL" to "TALB", "TP1" to "TPE1", "TP2" to "TPE2", "TCM" to "TCOM",
        "TCO" to "TCON", "TYE" to "TYER", "TOR" to "TORY", "TRK" to "TRCK", "TPA" to "TPOS",
        "TXX" to "TXXX",
    )

    private const val HEADER_SIZE = 10
    private const val FLAG_UNSYNC = 0x80
    private const val FLAG_EXTENDED = 0x40
    private const val FLAG_FOOTER = 0x10
    private const val MAX_TAGS = 8

    /** Covers get big, but not this big; a larger declared size is cut here rather than allocated. */
    private const val MAX_TAG_SIZE = 64L * 1024 * 1024
}
