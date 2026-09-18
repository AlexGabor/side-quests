package com.alexgabor.design.riso.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.risograph.inks.RisoMix
import com.alexgabor.design.riso.risograph.inks.color
import com.alexgabor.design.riso.risograph.inks.risoInk

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val lineWidth = RisoTheme.dimens.lineWidth
    val shape = RisoTheme.shapes.standardShape
    val colors = RisoTheme.colors
    val selectedBorder = RisoMix(
        colors.accent to 1f,
        unprinted = colors.accent,
    )
    val selectedBorderColor = selectedBorder.color()
    val cardStyle = Style {
        border(lineWidth, Color.Transparent) // reserving the space
        pressed {
            animate(toSpec = snap(), fromSpec = spring(stiffness = Spring.StiffnessLow)) {
                border(
                    width = lineWidth,
                    color = selectedBorderColor,
                )
            }
        }
        this.selected {
            animate(toSpec = snap(), fromSpec = spring(stiffness = Spring.StiffnessLow)) {
                border(
                    width = lineWidth,
                    color = selectedBorderColor,
                )
            }
        }
        shape(shape)
        hovered {
            background(colors.accent.copy(alpha = 0.4f))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val mutableStyleState = remember(interactionSource) { MutableStyleState(interactionSource) }
    mutableStyleState.isSelected = isSelected

    Box(
        modifier = modifier
            .clickable(enabled = true, onClick = onClick, interactionSource = interactionSource, indication = null)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown()
                        mutableStyleState.isPressed = true
                        waitForUpOrCancellation()
                        mutableStyleState.isPressed = false // Triggers instantly on finger up
                    }
                }
            }
            .semantics { selected = isSelected }
            .risoInk(selectedBorder)
            .styleable(mutableStyleState, cardStyle),
        content = {
            Heading3(
                text = text,
                modifier = Modifier.risoInk(RisoTheme.colors.content)
                    .padding(8.dp),
            )
        },
    )
}