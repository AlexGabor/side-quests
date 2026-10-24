package com.alexgabor.design.riso.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The demo's pages, for the iOS host. */
fun MainViewController(): UIViewController = ComposeUIViewController { RisoDemo() }
