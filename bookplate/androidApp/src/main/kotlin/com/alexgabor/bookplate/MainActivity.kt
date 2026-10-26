package com.alexgabor.bookplate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    /** Taps on the media notification, each shown once; a recreated activity starts with none. */
    private val showPlaying = Channel<Unit>(Channel.CONFLATED)
    private val showPlayingRequests = showPlaying.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && isShowPlaying(intent)) showPlaying.trySend(Unit)

        setContent {
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = true
            }
            App(showPlaying = showPlayingRequests)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isShowPlaying(intent)) showPlaying.trySend(Unit)
    }
}
