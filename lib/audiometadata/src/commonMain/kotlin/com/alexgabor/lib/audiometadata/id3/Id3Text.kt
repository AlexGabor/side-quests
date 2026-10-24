package com.alexgabor.lib.audiometadata.id3

/**
 * ID3v2 strings in their four encodings: 0 ISO-8859-1, 1 UTF-16 with BOM, 2 UTF-16BE, 3 UTF-8. Common
 * Kotlin only decodes UTF-8, so the others are done by hand.
 */
internal object Id3Text {

    /** A text frame's values: encoding byte, then NUL-separated strings. Blank values are dropped. */
    fun values(data: ByteArray): List<String> {
        if (data.isEmpty()) return emptyList()
        return decode(data, 1, data.size, data[0].toInt())
            .split('\u0000')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** The NUL-terminated string at [start], and where the data after its terminator begins. */
    fun terminated(data: ByteArray, start: Int, encoding: Int): Pair<String, Int> {
        if (isWide(encoding)) {
            var i = start
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return decode(data, start, i, encoding) to i + 2
                i += 2
            }
        } else {
            for (i in start until data.size) {
                if (data[i].toInt() == 0) return decode(data, start, i, encoding) to i + 1
            }
        }
        return decode(data, start, data.size, encoding) to data.size
    }

    fun decode(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        return when (encoding) {
            1 -> utf16(data, start, end, bigEndian = false)
            2 -> utf16(data, start, end, bigEndian = true)
            3 -> data.decodeToString(start, end)
            else -> latin1(data, start, end)
        }.replace("﻿", "")
    }

    fun latin1(data: ByteArray, start: Int, end: Int): String =
        CharArray(end - start) { (data[start + it].toInt() and 0xFF).toChar() }.concatToString()

    private fun isWide(encoding: Int) = encoding == 1 || encoding == 2

    /**
     * Each BOM switches the byte order for what follows, since every value in a multi-value frame may
     * carry its own. Without one, encoding 1 is read little-endian, as most writers do.
     */
    private fun utf16(data: ByteArray, start: Int, end: Int, bigEndian: Boolean): String {
        val out = StringBuilder((end - start) / 2)
        var big = bigEndian
        var i = start
        while (i + 1 < end) {
            val a = data[i].toInt() and 0xFF
            val b = data[i + 1].toInt() and 0xFF
            i += 2
            when {
                a == 0xFE && b == 0xFF -> big = true
                a == 0xFF && b == 0xFE -> big = false
                else -> out.append((if (big) a shl 8 or b else b shl 8 or a).toChar())
            }
        }
        return out.toString()
    }
}
