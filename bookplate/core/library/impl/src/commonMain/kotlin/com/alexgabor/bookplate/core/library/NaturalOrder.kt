package com.alexgabor.bookplate.core.library

/**
 * Orders `/`-separated relative paths the way people number files: segment by segment, runs of digits
 * by their value (`2` before `10`), other text ignoring case. The raw strings break any tie, so the
 * order is total.
 */
internal object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        val left = a.split('/')
        val right = b.split('/')
        for (i in 0 until minOf(left.size, right.size)) {
            val result = compareSegments(left[i], right[i])
            if (result != 0) return result
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    private fun compareSegments(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                val endA = a.digitsEnd(i)
                val endB = b.digitsEnd(j)
                val result = compareNumbers(a.substring(i, endA), b.substring(j, endB))
                if (result != 0) return result
                i = endA
                j = endB
            } else {
                val result = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (result != 0) return result
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    /** By value without parsing, so any length works; then fewer leading zeros first. */
    private fun compareNumbers(a: String, b: String): Int {
        val trimmedA = a.trimStart('0')
        val trimmedB = b.trimStart('0')
        return trimmedA.length.compareTo(trimmedB.length).takeIf { it != 0 }
            ?: trimmedA.compareTo(trimmedB).takeIf { it != 0 }
            ?: a.length.compareTo(b.length)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun String.digitsEnd(from: Int): Int {
        var end = from
        while (end < length && this[end].isAsciiDigit()) end++
        return end
    }
}
