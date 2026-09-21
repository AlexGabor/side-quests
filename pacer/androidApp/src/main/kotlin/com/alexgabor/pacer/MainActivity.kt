package com.alexgabor.pacer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.alexgabor.lib.launch.LaunchParameters
import com.alexgabor.lib.launch.launchParameters

class MainActivity : ComponentActivity() {

    // Read from the intent here rather than inside the content lambda, which runs again on every
    // recomposition.
    private var launch by mutableStateOf(LaunchParameters.Empty)

    // Bumped by every link opened while running. The app is composed under it, so a new link
    // starts from fresh state — the same thing iOS gets from `.id(launchUrl)` — while a rotation
    // or process death restores the same value and with it the back stack the user left.
    private var generation by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        generation = savedInstanceState?.getInt(GenerationKey) ?: 0
        launch = intent.launchParameters()

        setContent {
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = true
            }
            key(generation) {
                App(launch)
            }
        }
    }

    // singleTask: a link opened while Pacer is running arrives here instead of stacking another
    // activity on the task.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launch = intent.launchParameters()
        generation++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(GenerationKey, generation)
    }

    private companion object {
        const val GenerationKey = "generation"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
