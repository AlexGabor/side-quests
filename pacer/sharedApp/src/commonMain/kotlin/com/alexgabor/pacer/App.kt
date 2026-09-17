package com.alexgabor.pacer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.navigation.LocalDeepLink
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.extension.compose.asState
import com.alexgabor.lib.launch.LaunchParameters
import com.alexgabor.pacer.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject

@Composable
fun rememberAppState(
    settings: SettingsRepository = koinInject(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): AppState {
    return remember(settings, coroutineScope) {
        AppState(settings, coroutineScope)
    }
}

class AppState(
    settings: SettingsRepository,
    coroutineScope: CoroutineScope,
) {
    val risoEffectsEnabled: Boolean? by settings.risoEffectsEnabled
        .asState(initialValue = null, coroutineScope = coroutineScope)
}

@Composable
fun App(
    launch: LaunchParameters = LaunchParameters.Empty,
    state: AppState = rememberAppState(),
) {
    val deepLink = remember(launch) { pacerDeepLink(launch) }
    val risoEffectsEnabled = state.risoEffectsEnabled

    RisoTheme(effectsEnabled = risoEffectsEnabled != false) {
        // Settings arrive a frame or two after launch, and the navigation below is not composed
        // until they do. Providing the deep link out here means it is simply waiting when it is.
        CompositionLocalProvider(LocalDeepLink provides deepLink) {
            Crossfade(targetState = risoEffectsEnabled != null) { loaded ->
                if (loaded) {
                    RootNavigation()
                } else {
                    Box(Modifier.fillMaxSize().background(RisoTheme.colors.paper))
                }
            }
        }
    }
}

@Composable
@Preview
private fun AppPreview() {
    RisoTheme {
        RootNavigation()
    }
}
