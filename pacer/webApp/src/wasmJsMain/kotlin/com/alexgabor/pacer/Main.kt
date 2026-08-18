package com.alexgabor.pacer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Pacer in a browser — the web counterpart of `MainActivity`, desktop's `main` and iOS's
 * `MainViewController`.
 *
 * The whole app is [App], so this is only the seam. With no container named, the viewport mounts on
 * `<body>`, which the page sizes to fill the window.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { App() }
}
