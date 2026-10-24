package com.alexgabor.bookplate

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Bookplate opens from nothing but its icon, so there is nothing to pass in. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
