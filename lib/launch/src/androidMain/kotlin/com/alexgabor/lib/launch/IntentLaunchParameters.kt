package com.alexgabor.lib.launch

import android.content.Intent

/**
 * What this intent asks the app to start with.
 *
 * Three sources, in increasing precedence: the host of a `VIEW` uri, which names a screen so that
 * `pacer://settings` works without a query string; that uri's query parameters; and the intent
 * extras, which win because they are the explicit form — the one `adb shell am start --es` and
 * another app's `putExtra` both use.
 */
fun Intent.launchParameters(): LaunchParameters {
    val values = mutableMapOf<String, String>()

    data?.let { uri ->
        uri.host?.takeIf { it.isNotBlank() }?.let { values["screen"] = it }
        values += LaunchParameters.ofQueryString(uri.query).asMap()
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
