package com.alexgabor.pacer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.Icon
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.OnOff
import com.alexgabor.design.riso.layout.contentWidth
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.pacer.settings.PacerSettingsRepository
import com.alexgabor.pacer.settings.rememberPacerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun rememberSettingsScreenState(
    settings: PacerSettingsRepository = rememberPacerSettingsRepository(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): SettingsScreenState {
    return remember(settings, coroutineScope) { SettingsScreenState(settings, coroutineScope) }
}

class SettingsScreenState(
    private val settings: PacerSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    val risoEffectsEnabled: Boolean? by settings.risoEffectsEnabled
        .asState(initialValue = null, coroutineScope = coroutineScope)

    fun setRisoEffectsEnabled(enabled: Boolean) {
        coroutineScope.launch {
            settings.setRisoEffectsEnabled(enabled)
        }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsScreenState = rememberSettingsScreenState(),
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .contentWidth(available = maxWidth, max = RisoTheme.dimens.contentMaxWidth)
                .fillMaxSize()
        ) {
            SettingsHeader(
                onBackClick = onBackClick,
                risoEffectsEnabled = state.risoEffectsEnabled,
                onRisoEffectToggle = { enabled ->
                    state.setRisoEffectsEnabled(enabled)
                },
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                ),
            )

            // Nothing to settle yet — the screen exists so the gear has somewhere to go.
        }
    }
}

@Composable
private fun SettingsHeader(
    onBackClick: () -> Unit,
    risoEffectsEnabled: Boolean?,
    onRisoEffectToggle: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Column(modifier.risoInk(RisoTheme.colors.content, RisoTheme.colors.accent)) {
            Heading1(
                text = "Settings",
                modifier = Modifier.fillMaxWidth()
                    .padding(RisoTheme.dimens.screenPadding),
                startContent = {
                    Icon(
                        type = IconType.Back,
                        onClick = onBackClick,
                    )
                }
            )

            if (risoEffectsEnabled != null) {
                RisoEffectSetting(
                    enabled = risoEffectsEnabled,
                    onRisoEffectToggle = onRisoEffectToggle,
                    modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
                )
            }
        }
    }
}

@Composable
fun RisoEffectSetting(
    enabled: Boolean,
    onRisoEffectToggle: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Body(
            text = "Riso effects",
            modifier = Modifier.weight(1f)
                .padding(vertical = RisoTheme.dimens.screenPadding),
        )
        ButtonGroup(
            selected = if (enabled) OnOff.On else OnOff.Off,
            *OnOff.entries.toTypedArray(),
            onSelect = { onRisoEffectToggle(it == OnOff.On) },
        )
    }
}
