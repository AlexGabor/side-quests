package com.alexgabor.lib.audiometadata

/** Decoding for text that doesn't say its encoding: RIFF `INFO` values and cue sheets. */
internal object Text {

    /**
     * UTF-8 when the bytes decode cleanly, else Windows-1252. Accented Latin letters on their own almost
     * never form valid UTF-8, and Windows-1252 is what Windows tools write in Western locales.
     */
    fun utf8OrWindows1252(data: ByteArray, start: Int = 0, end: Int = data.size): String {
        if (start >= end) return ""
        return try {
            data.decodeToString(start, end, throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            windows1252(data, start, end)
        }
    }

    fun windows1252(data: ByteArray, start: Int, end: Int): String = CharArray(end - start) {
        val b = data[start + it].toInt() and 0xFF
        if (b in 0x80..0x9F) WINDOWS_1252[b - 0x80] else b.toChar()
    }.concatToString()

    /** 0x80–0x9F; the five unassigned bytes map to the C1 controls, as Windows itself does. */
    private val WINDOWS_1252 = charArrayOf(
        '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
        '\u0090', '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
    )
}
