package com.alexgabor.lib.launch

/**
 * What these command line arguments ask the app to start with.
 *
 * Accepts `--key=value` and `--key value`, and treats a flag with nothing after it as present —
 * `--verbose` reads back as true. A bare argument that isn't preceded by a flag is ignored rather
 * than positional, because a launch parameter always has a name on every other platform.
 */
fun Array<String>.launchParameters(): LaunchParameters {
    val values = mutableMapOf<String, String>()

    var index = 0
    while (index < size) {
        val argument = this[index]
        index++

        if (!argument.startsWith("--")) continue
        val body = argument.removePrefix("--")
        if (body.isEmpty()) continue

        if ('=' in body) {
            values[body.substringBefore('=')] = body.substringAfter('=')
            continue
        }

        // `--key value`, unless what follows is the next flag, in which case this one is a flag too.
        val next = getOrNull(index)
        if (next != null && !next.startsWith("--")) {
            values[body] = next
            index++
        } else {
            values[body] = ""
        }
    }

    return if (values.isEmpty()) LaunchParameters.Empty else LaunchParameters(values)
}
