package com.alexgabor.lib.launch

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What an app was started with, flattened to strings.
 *
 * Android intent extras, a browser query string and command line arguments are all key/value bags,
 * so that is the shape everything is reduced to at the boundary. Reading is deliberately total:
 * every accessor answers null rather than throwing, because launch input is the one thing a user
 * can type by hand and a typo in a URL should give the default screen, not a crash on start.
 */
class LaunchParameters(private val values: Map<String, String>) {

    val isEmpty: Boolean get() = values.isEmpty()

    fun asMap(): Map<String, String> = values

    operator fun get(key: String): String? = values[key]

    operator fun contains(key: String): Boolean = key in values

    fun double(key: String): Double? = values[key]?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun long(key: String): Long? = values[key]?.toLongOrNull()

    fun int(key: String): Int? = values[key]?.toIntOrNull()

    /** Present-but-valueless counts as true, so `--verbose` works as well as `--verbose=true`. */
    fun boolean(key: String): Boolean? = when (values[key]?.lowercase()) {
        null -> null
        "", "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    /**
     * A duration written the way a runner writes one: `"1:23:45"`, `"5:30"`, or plain seconds.
     *
     * Only the last component may be fractional, and every component is unbounded — `"90:00"` is
     * ninety minutes, not an error — because clamping input the user typed on purpose would be
     * more surprising than honouring it.
     */
    fun duration(key: String): Duration? {
        val parts = values[key]?.split(':') ?: return null
        if (parts.size > 3) return null

        var total = 0.0
        for ((index, part) in parts.withIndex()) {
            val value = part.toDoubleOrNull() ?: return null
            if (value < 0.0 || !value.isFinite()) return null
            // Rightmost component is always seconds, so the multiplier depends on how many follow.
            val multiplier = POSITION_MULTIPLIERS[parts.size - 1 - index]
            total += value * multiplier
        }
        return total.seconds
    }

    /** By constant name, case-insensitively; an unknown name is null rather than an exception. */
    inline fun <reified E : Enum<E>> enum(key: String): E? {
        val name = this[key] ?: return null
        return enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * `"a=1&b=2"`, in the order the map holds them, which [ofQueryString] reads back as these same
     * parameters.
     *
     * No leading `?` or `#`: which of the two it goes after is the caller's choice.
     */
    fun toQueryString(): String =
        values.entries.joinToString("&") { (name, value) ->
            "${name.percentEncoded()}=${value.percentEncoded()}"
        }

    override fun equals(other: Any?): Boolean =
        this === other || (other is LaunchParameters && values == other.values)

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "LaunchParameters($values)"

    companion object {
        val Empty = LaunchParameters(emptyMap())

        private val POSITION_MULTIPLIERS = doubleArrayOf(1.0, 60.0, 3600.0)

        private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1000)

        /**
         * [value] the way [duration] reads it: `"5:30"` under an hour, `"1:45:30"` from one up.
         *
         * Rounded to whole seconds, which is as fine as anything a runner writes by hand, or to
         * [fractionDigits] places of a second — `"5:41.27"` — for a caller that shows that much.
         * Trailing zeros are dropped, and the point with them, so a whole second is still written
         * `"6:00"`. Rounded before it is split, so `59.996` seconds carries to `"1:00"`. Null for
         * what [duration] would refuse — a negative or infinite duration has no spelling.
         */
        fun formatDuration(value: Duration, fractionDigits: Int = 0): String? {
            if (!value.isFinite() || value < Duration.ZERO) return null
            require(fractionDigits in 0..3) { "Durations are held to the millisecond" }

            val perSecond = POWERS_OF_TEN[fractionDigits]
            val grain = 1000 / perSecond
            val units = (value.inWholeMilliseconds + grain / 2) / grain
            val seconds = units / perSecond
            val fraction = (units % perSecond).toString().padStart(fractionDigits, '0').trimEnd('0')
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secondsPart = (seconds % 60).twoDigits() + if (fraction.isEmpty()) "" else ".$fraction"

            return if (hours > 0) {
                "$hours:${minutes.twoDigits()}:$secondsPart"
            } else {
                "$minutes:$secondsPart"
            }
        }

        private fun Long.twoDigits(): String = toString().padStart(2, '0')

        /**
         * Parses `"?a=1&b=2"`, `"a=1&b=2"` or a whole URL.
         *
         * Shared by the web, iOS and desktop entry points — only Android has a launch input that
         * isn't already a query string.
         */
        fun ofQueryString(query: String?): LaunchParameters {
            if (query.isNullOrBlank()) return Empty

            // Accepts a whole url, a "?a=1" search, a "#a=1" fragment, or a bare "a=1".
            val body = query.trimStart('#', '?').substringAfterLast('?').substringBefore('#')

            val values = body
                .split('&')
                .filter { it.isNotEmpty() }
                .mapNotNull { pair ->
                    val name = pair.substringBefore('=').percentDecoded()
                    if (name.isEmpty()) return@mapNotNull null
                    name to pair.substringAfter('=', missingDelimiterValue = "").percentDecoded()
                }
                .toMap()

            return if (values.isEmpty()) Empty else LaunchParameters(values)
        }

        /**
         * What a url the app was opened with asks for.
         *
         * The host of a custom-scheme url names a screen, so `pacer://settings` works without a
         * query string; the query is read on top of it and wins, so `?screen=` still decides. An
         * http(s) url is an app link, whose host is the website's domain rather than a screen, so
         * there the screen can only come from the query, the same as it does in the browser.
         */
        fun ofUrl(url: String?): LaunchParameters {
            if (url.isNullOrBlank()) return Empty

            val values = mutableMapOf<String, String>()

            val scheme = url.substringBefore("://", missingDelimiterValue = "")
            if (scheme.isNotEmpty() && scheme.lowercase() !in WebSchemes) {
                val host = url.substringAfter("://")
                    .substringBefore('/').substringBefore('?').substringBefore('#')
                    .substringAfterLast('@').substringBefore(':')
                    .percentDecoded()
                if (host.isNotBlank()) values["screen"] = host
            }

            // Only what follows a `?`: a url without one has no query, and reading it whole would
            // turn `pacer://settings` itself into a parameter.
            if ('?' in url) values += ofQueryString(url.substringAfter('?')).asMap()

            return if (values.isEmpty()) Empty else LaunchParameters(values)
        }

        private val WebSchemes = setOf("http", "https")
    }
}

/**
 * The inverse of [percentDecoded]: everything but the unreserved characters becomes a `%XX` escape
 * of its UTF-8 bytes.
 *
 * `:` is left alone too — a query may carry it as written, and escaping it would turn every
 * `pace=5:00` into `pace=5%3A00` for no reader's benefit. `+` is escaped, because the decoder reads
 * a bare one as a space.
 */
private fun String.percentEncoded(): String {
    if (all { it.isUnescaped() }) return this

    val encoded = StringBuilder(length)
    // A character's bytes are escaped together — a surrogate pair is one character to the encoder.
    for (byte in encodeToByteArray()) {
        val char = (byte.toInt() and 0xFF).toChar()
        if (char.isUnescaped()) {
            encoded.append(char)
        } else {
            encoded.append('%')
            encoded.append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
            encoded.append(HEX_DIGITS[byte.toInt() and 0xF])
        }
    }
    return encoded.toString()
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun Char.isUnescaped(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~:"

/**
 * Percent-decoding, plus the `+`-for-space that query strings inherited from form encoding.
 *
 * Hand-rolled because the multiplatform standard library has no URL decoder, and reaching for a
 * platform one would mean four implementations of something this small. A malformed escape is left
 * as written rather than throwing — same forgiveness as everything else here.
 */
private fun String.percentDecoded(): String {
    if ('%' !in this && '+' !in this) return this

    val decoded = StringBuilder(length)
    // Escapes are bytes, and a character can be several of them, so they are gathered up and
    // decoded together. Anything else is already a character and is appended as one — encoding it
    // to bytes first would split a surrogate pair and turn an unescaped emoji into two question
    // marks.
    val escaped = ArrayList<Byte>(4)

    fun flush() {
        if (escaped.isEmpty()) return
        decoded.append(escaped.toByteArray().decodeToString())
        escaped.clear()
    }

    var index = 0
    while (index < length) {
        when (val char = this[index]) {
            '%' -> {
                val byte = if (index + 3 <= length) {
                    substring(index + 1, index + 3).toIntOrNull(radix = 16)
                } else {
                    null
                }
                if (byte == null) {
                    flush()
                    decoded.append(char)
                    index++
                } else {
                    escaped.add(byte.toByte())
                    index += 3
                }
            }
            '+' -> {
                flush()
                decoded.append(' ')
                index++
            }
            else -> {
                flush()
                decoded.append(char)
                index++
            }
        }
    }
    flush()

    return decoded.toString()
}
