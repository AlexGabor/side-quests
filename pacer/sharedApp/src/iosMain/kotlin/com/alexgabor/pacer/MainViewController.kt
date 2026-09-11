package com.alexgabor.pacer

import androidx.compose.ui.window.ComposeUIViewController
import com.alexgabor.lib.launch.LaunchParameters
import platform.UIKit.UIViewController

/**
 * @param launchUrl the url the app was opened with, or null for a plain launch.
 *
 * Explicitly nullable rather than defaulted, so the Objective-C export stays a single selector that
 * Swift can always call the same way.
 */
fun MainViewController(launchUrl: String?): UIViewController =
    ComposeUIViewController { App(LaunchParameters.ofQueryString(launchUrl)) }
