package com.alexgabor.pacer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alexgabor.lib.launch.browserLaunchParameters
import com.alexgabor.pacer.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    val launch = browserLaunchParameters()
    ComposeViewport { App(launch) }
}
