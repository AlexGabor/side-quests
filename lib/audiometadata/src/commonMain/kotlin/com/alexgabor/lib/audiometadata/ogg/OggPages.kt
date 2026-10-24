package com.alexgabor.lib.audiometadata.ogg

import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.le32
import com.alexgabor.lib.audiometadata.le64
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u8

/** One Ogg page header: 27 bytes, then a lacing table of [lacing] segment sizes. */
internal class OggPage(
    val flags: Int,
    val granule: Long,
    val serial: Long,
    val lacing: IntArray,
    val headerSize: Int,
) {
    val bodySize: Int get() = lacing.sum()
    val size: Int get() = headerSize + bodySize
    val isFirst: Boolean get() = flags and 0x02 != 0

    companion object {
        /** The page header at [i] of [b], or `null` when it isn't one or doesn't fit. */
        fun parse(b: ByteArray, i: Int): OggPage? {
            if (i < 0 || i + HEADER > b.size || !b.startsWith("OggS", i) || b[i + 4].toInt() != 0) return null
            val segments = b.u8(i + 26)
            if (i + HEADER + segments > b.size) return null
            return OggPage(
                flags = b.u8(i + 5),
                granule = b.le64(i + 6),
                serial = b.le32(i + 14),
                lacing = IntArray(segments) { b.u8(i + HEADER + it) },
                headerSize = HEADER + segments,
            )
        }

        const val HEADER = 27
    }
}

/** Ogg structure: pages, packets across pages, and the last page found from the end. */
internal object OggPages {

    /** The page header at [position], reading only the header and lacing table. */
    fun header(reader: Reader, position: Long): OggPage? {
        val fixed = reader.bytes(position, OggPage.HEADER)
        if (fixed.size < OggPage.HEADER) return null
        return OggPage.parse(fixed + reader.bytes(position + OggPage.HEADER, fixed.u8(26)), 0)
    }

    /**
     * The first [count] packets of the stream the page at [start] begins. Pages of other streams are
     * skipped. A packet cut off by the end of the source, or by [maxPacket], is returned as far as it got.
     */
    fun packets(reader: Reader, start: Long, count: Int, maxPacket: Int): Pair<OggPage, List<ByteArray>>? {
        val first = header(reader, start)?.takeIf { it.isFirst } ?: return null
        val packets = mutableListOf<ByteArray>()
        var current = Packet(maxPacket)
        var position = start
        var pages = 0
        while (packets.size < count && pages++ < MAX_PAGES) {
            val page = header(reader, position) ?: break
            val bodyStart = position + page.headerSize
            position += page.size
            if (page.serial != first.serial) continue
            val body = reader.bytes(bodyStart, page.bodySize)
            var offset = 0
            for (lace in page.lacing) {
                current.add(body, offset, minOf(offset + lace, body.size))
                offset += lace
                if (lace < 255) {
                    packets += current.bytes()
                    current = Packet(maxPacket)
                    if (packets.size == count) break
                }
            }
            if (body.size < page.bodySize) break
        }
        if (packets.size < count && current.size > 0) packets += current.bytes()
        return first to packets
    }

    /**
     * The granule position of the last page of stream [serial], looking back from the end of the source
     * through growing windows. A candidate counts only as a whole page with a valid CRC.
     */
    fun lastGranule(reader: Reader, from: Long, serial: Long): Long? {
        for (window in WINDOWS) {
            val length = minOf(window.toLong(), reader.size - from).toInt()
            if (length <= 0) return null
            val b = reader.bytes(reader.size - length, length)
            var i = b.size - OggPage.HEADER
            while (i >= 0) {
                if (b[i] == 'O'.code.toByte()) {
                    val page = OggPage.parse(b, i)
                    if (page != null && page.serial == serial && page.granule != -1L &&
                        i + page.size <= b.size && OggCrc.matches(b, i, page.size)
                    ) {
                        return page.granule
                    }
                }
                i--
            }
            if (length.toLong() == reader.size - from) return null
        }
        return null
    }

    /** Growable bytes, chunked so a spanning packet isn't copied once per page. */
    private class Packet(private val max: Int) {
        private val chunks = mutableListOf<ByteArray>()
        var size = 0
            private set

        fun add(b: ByteArray, from: Int, to: Int) {
            val n = minOf(to - from, max - size)
            if (n <= 0) return
            chunks += b.copyOfRange(from, from + n)
            size += n
        }

        fun bytes(): ByteArray {
            val out = ByteArray(size)
            var p = 0
            for (c in chunks) {
                c.copyInto(out, p)
                p += c.size
            }
            return out
        }
    }

    private val WINDOWS = intArrayOf(16 * 1024, 64 * 1024, 256 * 1024)
    private const val MAX_PAGES = 1024
}

/** The Ogg page checksum: CRC-32, polynomial 0x04C11DB7, no reflection, initial value 0. */
internal object OggCrc {

    fun matches(b: ByteArray, start: Int, length: Int): Boolean {
        val stored = b.le32(start + 22)
        var crc = 0
        for (i in start until start + length) {
            // The checksum field itself counts as zeros.
            val byte = if (i - start in 22..25) 0 else b[i].toInt() and 0xFF
            crc = (crc shl 8) xor TABLE[((crc ushr 24) xor byte) and 0xFF]
        }
        return (crc.toLong() and 0xFFFFFFFFL) == stored
    }

    fun of(b: ByteArray): Int {
        var crc = 0
        for (byte in b) crc = (crc shl 8) xor TABLE[((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    private val TABLE = IntArray(256) { n ->
        var r = n shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }
}
