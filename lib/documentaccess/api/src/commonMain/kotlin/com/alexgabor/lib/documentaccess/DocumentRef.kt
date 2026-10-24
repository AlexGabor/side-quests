package com.alexgabor.lib.documentaccess

/**
 * A file or folder the user gave the app access to, or a file listed inside such a folder. It keeps
 * working across restarts for as long as the platform keeps the grant, and goes to and from a single
 * string through [asString] and [parse] so it can be stored.
 *
 * Only an implementation of [DocumentAccess] makes or reads the parts, and what they hold is up to it:
 * [root] is the picked item's platform token (a `content://` URI on Android, bookmark data on iOS) and
 * [child], `null` for the picked item itself, says which file inside it is meant.
 */
data class DocumentRef(
    val root: String,
    val child: String?,
    val isFolder: Boolean,
) {

    /** The string [parse] turns back into this reference. */
    fun asString(): String = buildString {
        append(PREFIX).append(SEPARATOR)
        append(if (isFolder) FOLDER else FILE).append(SEPARATOR)
        append(escape(root))
        if (child != null) append(SEPARATOR).append(escape(child))
    }

    companion object {

        /** The reference [value] was made from by [asString], or `null` when it isn't one. */
        fun parse(value: String): DocumentRef? {
            val parts = value.split(SEPARATOR)
            if (parts.size !in 3..4 || parts[0] != PREFIX) return null
            val isFolder = when (parts[1]) {
                FOLDER -> true
                FILE -> false
                else -> return null
            }
            val root = unescape(parts[2]) ?: return null
            val child = if (parts.size == 4) unescape(parts[3]) ?: return null else null
            return DocumentRef(root, child, isFolder)
        }

        // Versioned, so the form can change later without misreading old rows.
        private const val PREFIX = "docref1"
        private const val SEPARATOR = ';'
        private const val FOLDER = "D"
        private const val FILE = "F"

        // Only the escape character and the separator are escaped, so URIs that are already
        // percent-encoded survive untouched apart from their own `%`.
        private fun escape(part: String): String = part.replace("%", "%25").replace(";", "%3B")

        private fun unescape(part: String): String? {
            val out = StringBuilder(part.length)
            var i = 0
            while (i < part.length) {
                val c = part[i]
                if (c != '%') {
                    out.append(c)
                    i++
                    continue
                }
                when (part.substring(i, minOf(i + 3, part.length))) {
                    "%25" -> out.append('%')
                    "%3B" -> out.append(';')
                    else -> return null
                }
                i += 3
            }
            return out.toString()
        }
    }
}
