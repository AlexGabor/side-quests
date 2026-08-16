package com.alexgabor.stamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alexgabor.stamp.export.MediaStoreIconExporter

/**
 * A launcher for [StampScreen].
 *
 * No `@Preview` anywhere in this app. Both the sheet and the ink are `RuntimeShader`s, which only
 * run under hardware rendering — Layoutlib does not execute one, so a preview would show an
 * unprinted icon and quietly mislead about what is going to be exported. It needs a device.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val exporter = MediaStoreIconExporter(applicationContext)
        setContent {
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = true
            }
            App(exporter)
        }
    }
}
