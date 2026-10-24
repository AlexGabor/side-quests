package com.alexgabor.lib.audiometadata

/**
 * Bounds-checked reads over a [ByteSource]. A failing or short source reads as if it ended there, so
 * parsers only ever see fewer bytes than they asked for, never an exception from the source.
 */
internal class Reader(private val source: ByteSource) {
    val size: Long = try {
        source.size.coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }

    /** Up to [length] bytes at [position], never more than the source has left. */
    fun bytes(position: Long, length: Int): ByteArray {
        if (position < 0 || position >= size || length <= 0) return EMPTY
        val wanted = minOf(length.toLong(), size - position).toInt()
        val out = ByteArray(wanted)
        var filled = 0
        while (filled < wanted) {
            val chunk = try {
                source.read(position + filled, wanted - filled)
            } catch (_: Exception) {
                break
            }
            if (chunk.isEmpty()) break
            val n = minOf(chunk.size, wanted - filled)
            chunk.copyInto(out, filled, 0, n)
            filled += n
        }
        return if (filled == wanted) out else out.copyOf(filled)
    }

    companion object {
        val EMPTY = ByteArray(0)
    }
}

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

internal fun ByteArray.u16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)

internal fun ByteArray.u24(i: Int): Int = (u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2)

internal fun ByteArray.u32(i: Int): Long =
    (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()

/** Four 7-bit bytes, as ID3v2 writes sizes. */
internal fun ByteArray.syncsafe(i: Int): Long =
    (u8(i).toLong() and 0x7F shl 21) or (u8(i + 1).toLong() and 0x7F shl 14) or
        (u8(i + 2).toLong() and 0x7F shl 7) or (u8(i + 3).toLong() and 0x7F)

internal fun ByteArray.ascii(i: Int, length: Int): String =
    if (i < 0 || i + length > size) "" else CharArray(length) { (this[i + it].toInt() and 0xFF).toChar() }.concatToString()

internal fun ByteArray.startsWith(prefix: String, at: Int = 0): Boolean = ascii(at, prefix.length) == prefix

/** A big-endian 64-bit value; negative when the top bit is set. */
internal fun ByteArray.u64(i: Int): Long = (u32(i) shl 32) or u32(i + 4)

internal fun ByteArray.le16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

internal fun ByteArray.le32(i: Int): Long =
    u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)

/** A little-endian 64-bit value; negative when the top bit is set. */
internal fun ByteArray.le64(i: Int): Long = le32(i) or (le32(i + 4) shl 32)
