package com.alexgabor.design.riso.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.risograph.inks.onRisoPaper
import com.alexgabor.design.riso.risograph.inks.risoInk

interface ButtonGroupItem {
    val text: String
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun <T> ButtonGroup(
    selected: T,
    vararg items: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) where T : Enum<T>, T : ButtonGroupItem {
    Row(
        modifier = modifier
            .risoInk(RisoTheme.colors.content)
            .requiredWidth(IntrinsicSize.Max)
            .height(IntrinsicSize.Min)
            .clip(RisoTheme.shapes.pillShape)
            .border(
                width = RisoTheme.dimens.lineWidth,
                color = RisoTheme.colors.content,
                shape = RisoTheme.shapes.pillShape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(RisoTheme.dimens.lineWidth)
                        .background(RisoTheme.colors.content)
                )
            }

            val isSelected = item == selected

            val textColor by animateColorAsState(
                targetValue = if (isSelected) RisoTheme.colors.paper else RisoTheme.colors.content
            )

            val shape = when (index) {
                0 -> RisoTheme.shapes.pillShape
                    .copy(topEnd = CornerSize(0f), bottomEnd = CornerSize(0f))

                items.size - 1 ->
                    RisoTheme.shapes.pillShape
                        .copy(topStart = CornerSize(0f), bottomStart = CornerSize(0f))

                else -> RectangleShape
            }

            val colors = RisoTheme.colors

            val segmentStyle = Style {
                selected {
                    animate {
                        background(colors.accent.onRisoPaper())
                    }
                }
                hovered {
                    background(colors.accent.copy(alpha = 0.4f))
                }
                shape(shape)
                contentPadding(horizontal = 24.dp, vertical = 12.dp)
            }

            val interactionSource = remember { MutableInteractionSource() }
            val styleState =
                rememberUpdatedStyleState(interactionSource) { it.isSelected = isSelected }

            Box(
                modifier = Modifier
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        onClick = { onSelect(item) },
                    )
                    .risoInk(RisoTheme.colors.accent)
                    .styleable(styleState, segmentStyle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.text,
                    color = textColor,
                    textStyle = RisoTheme.typography.body,
                    modifier = if (!isSelected) Modifier.risoInk(RisoTheme.colors.content) else Modifier
                )
            }
        }
    }
}

enum class OnOff(override val text: String) : ButtonGroupItem {
    On("On"),
    Off("Off"),
}

private enum class PreviewUnit(override val text: String) : ButtonGroupItem {
    Kilometers("km"),
    Miles("mi"),
}

@Preview
@Composable
fun ButtonGroupPreview() {
    RisoTheme {
        var unit by remember { mutableStateOf(PreviewUnit.Kilometers) }
        Box(
            modifier = Modifier
                .background(RisoTheme.colors.paper)
                .padding(RisoTheme.dimens.screenPadding)
        ) {
            ButtonGroup(
                selected = unit,
                *PreviewUnit.entries.toTypedArray(),
                onSelect = { unit = it },
            )
        }
    }
}
