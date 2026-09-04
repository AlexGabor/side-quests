package com.alexgabor.pacer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.Icon
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.OnOff
import com.alexgabor.design.riso.layout.contentWidth
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
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .contentWidth(available = maxWidth, max = RisoTheme.dimens.contentMaxWidth)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SettingsHeader(
                onBackClick = onBackClick,
            )

            val risoEffectsEnabled = state.risoEffectsEnabled
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (risoEffectsEnabled != null) {
                    item {
                        RisoEffectSetting(
                            enabled = risoEffectsEnabled,
                            onRisoEffectToggle = { enabled -> state.setRisoEffectsEnabled(enabled) },
                            modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
                        )
                    }
                }
            }

            val uriHandler = LocalUriHandler.current
            Body(
                text = "Made by Alex Gabor ↗",
                modifier = Modifier
                    .clickable(onClick = { uriHandler.openUri("https://alexgabor.com") })
                    .padding(RisoTheme.dimens.screenPadding),
            )
        }
    }
}

@Composable
private fun SettingsHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(modifier) {
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
