package com.alexgabor.lib.launch

import android.content.Intent

/**
 * What this intent asks the app to start with.
 *
 * Two sources, in increasing precedence: the `VIEW` uri, read by [LaunchParameters.ofUrl] the same
 * way every platform reads the url it was opened with; and the intent extras, which win because they
 * are the explicit form — the one `adb shell am start --es` and another app's `putExtra` both use.
 */
fun Intent.launchParameters(): LaunchParameters {
    // toString keeps the uri encoded: the parser decodes, and decoding twice would mangle an escaped
    // `%` or `+`.
    val values = LaunchParameters.ofUrl(data?.toString()).asMap().toMutableMap()

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
