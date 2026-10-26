package com.alexgabor.bookplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        get<ShowPlaying>().follow(this, savedInstanceState)

        setContent {
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = true
            }
            App()
        }
    }
}
