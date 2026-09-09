package com.alexgabor.lib.launch

import kotlin.js.ExperimentalWasmJsInterop

/**
 * What the page url asks the app to start with.
 *
 * Both the query string and the fragment are read, so `?screen=settings` and `#screen=settings`
 * behave the same; a static host that swallows query strings still leaves the fragment intact.
 * Read through `js` rather than `kotlinx-browser` to keep this module free of a dependency it
 * would use twice.
 */
fun browserLaunchParameters(): LaunchParameters {
    val query = LaunchParameters.ofQueryString(locationSearch()).asMap()
    val fragment = LaunchParameters.ofQueryString(locationHash()).asMap()

    // The fragment is the more specific of the two — it is what a link hands over last.
    val values = query + fragment
    return if (values.isEmpty()) LaunchParameters.Empty else LaunchParameters(values)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationSearch(): String = js("window.location.search")

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationHash(): String = js("window.location.hash")
