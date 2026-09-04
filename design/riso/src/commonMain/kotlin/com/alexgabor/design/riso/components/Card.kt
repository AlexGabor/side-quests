package com.alexgabor.design.riso.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.RisoMix
import com.alexgabor.design.riso.risograph.inks.color
import com.alexgabor.design.riso.risograph.inks.risoInk

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun Card(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val lineWith = RisoTheme.dimens.lineWidth
    val shape = RisoTheme.shapes.standardShape
    val colors = RisoTheme.colors
    val selectedBorder = RisoMix(
        colors.content to .5f,
        colors.accent to 1f,
        unprinted = colors.accent,
    )
    val selectedBorderColor = selectedBorder.color()
    val cardStyle = Style {
        border(
            width = lineWith,
            color = colors.content,
        )
        this.selected {
            animate(spring(stiffness = Spring.StiffnessLow)) {
                border(
                    width = lineWith * 2,
                    color = selectedBorderColor,
                )
            }
        }
        shape(shape)
        hovered {
            background(colors.content.copy(alpha = 0.25f))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isSelected = isSelected }

    Box(
        modifier = modifier
            .clickable(enabled = true, onClick = onClick, interactionSource = interactionSource, indication = null)
            .risoInk(selectedBorder)
            .styleable(styleState, cardStyle),
        content = content,
    )
}
