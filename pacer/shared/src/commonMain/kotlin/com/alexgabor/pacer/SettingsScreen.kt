package com.alexgabor.pacer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.components.Icon
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.layout.contentWidth
import com.alexgabor.design.riso.risograph.inks.risoInk

@Composable
fun SettingsScreen(
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
                        modifier = Modifier
                            .clickable(onClick = onBackClick)
                    )
                }
            )
        }
    }
}
