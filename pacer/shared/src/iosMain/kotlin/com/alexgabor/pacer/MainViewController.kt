package com.alexgabor.pacer

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Pacer as something UIKit can host — the iOS counterpart of `MainActivity` and desktop's `main`.
 *
 * The whole app is [App], so this is only the seam: everything below it is the same code the other
 * two run. Named so it reaches Swift as `MainViewControllerKt.MainViewController()`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
