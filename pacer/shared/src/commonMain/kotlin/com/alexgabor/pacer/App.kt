package com.alexgabor.pacer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.extension.compose.asState
import com.alexgabor.pacer.settings.PacerSettingsRepository
import com.alexgabor.pacer.settings.rememberPacerSettingsRepository
import kotlinx.coroutines.CoroutineScope

@Composable
fun rememberAppState(
    settings: PacerSettingsRepository = rememberPacerSettingsRepository(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): AppState {
    return remember(settings, coroutineScope) {
        AppState(settings, coroutineScope)
    }
}

class AppState(
    settings: PacerSettingsRepository,
    coroutineScope: CoroutineScope,
) {
    val risoEffectsEnabled: Boolean? by settings.risoEffectsEnabled
        .asState(initialValue = null, coroutineScope = coroutineScope)
}

@Composable
fun App(
    state: AppState = rememberAppState()
) {
    val risoEffectsEnabled = state.risoEffectsEnabled
    RisoTheme(effectsEnabled = risoEffectsEnabled != false) {
        if (risoEffectsEnabled == null) {
            Box(Modifier.fillMaxSize().background(RisoTheme.colors.paper))
        } else {
            RootNavigation()
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
