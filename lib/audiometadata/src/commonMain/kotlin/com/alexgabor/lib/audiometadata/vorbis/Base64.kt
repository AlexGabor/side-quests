package com.alexgabor.lib.audiometadata.vorbis

/**
 * A lenient base64 decoder: whitespace and anything else outside the alphabet is skipped, padding is
 * optional, and the URL-safe `-` and `_` are accepted. Taggers wrap long values, which strict decoders
 * reject.
 */
internal object Base64 {

    fun decode(text: String): ByteArray {
        val out = ByteArray(text.length * 3 / 4 + 3)
        var n = 0
        var bits = 0
        var buffer = 0
        for (c in text) {
            if (c == '=') break
            val value = valueOf(c)
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[n++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.copyOf(n)
    }

    private fun valueOf(c: Char): Int = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> -1
    }
}
