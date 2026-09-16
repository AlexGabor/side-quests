package com.alexgabor.lib.launch

import android.content.Intent

/**
 * What this intent asks the app to start with.
 *
 * Three sources, in increasing precedence: the host of a custom-scheme `VIEW` uri, which names a
 * screen so that `pacer://settings` works without a query string; that uri's query parameters; and
 * the intent extras, which win because they are the explicit form — the one
 * `adb shell am start --es` and another app's `putExtra` both use.
 *
 * An http(s) uri is an app link, whose host is the website's domain rather than a screen — there
 * the screen can only come from the query, the same as it does in the browser.
 */
fun Intent.launchParameters(): LaunchParameters {
    val values = mutableMapOf<String, String>()

    data?.let { uri ->
        if (uri.scheme !in WebSchemes) {
            uri.host?.takeIf { it.isNotBlank() }?.let { values["screen"] = it }
        }
        // Still encoded: the parser decodes, and decoding twice would mangle an escaped `%` or `+`.
        values += LaunchParameters.ofQueryString(uri.encodedQuery).asMap()
    }

    // Extras are typed, and anything can be put in one; whatever it is, it is read as the string
    // the parsers here expect. `getString` alone would silently drop an `--ei`-style integer.
    extras?.let { extras ->
        @Suppress("DEPRECATION")
        extras.keySet().forEach { key ->
            extras.get(key)?.toString()?.let { values[key] = it }
        }
    }

    return if (values.isEmpty()) LaunchParameters.Empty else LaunchParameters(values)
}

private val WebSchemes = setOf("http", "https")
