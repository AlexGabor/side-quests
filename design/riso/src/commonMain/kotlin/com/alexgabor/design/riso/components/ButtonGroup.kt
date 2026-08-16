package com.alexgabor.design.riso.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.inks.onRisoPaper

interface ButtonGroupItem {
    val text: String
}

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
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) RisoTheme.colors.accent.onRisoPaper() else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )

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

            Box(
                modifier = Modifier
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(item) },
                    )
                    .risoInk(RisoTheme.colors.accent)
                    .background(backgroundColor, shape = shape)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
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
