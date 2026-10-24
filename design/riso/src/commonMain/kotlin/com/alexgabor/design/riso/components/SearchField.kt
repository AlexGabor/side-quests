package com.alexgabor.design.riso.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.risograph.inks.RisoMix
import com.alexgabor.design.riso.risograph.inks.color
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper

/** How strongly the placeholder is printed, so it reads as a hint rather than as something typed. */
private const val PlaceholderAlpha = 0.7f

private val MinHeight = 48.dp

/**
 * A single-line text input for searching: a search icon at the start, and a button that clears the
 * text at the end once there is any. Drawn like a [Card]: while the field has focus its border
 * doubles and prints as a selected card's does, in the accent over half-strength content.
 *
 * @param placeholder shown while [value] is empty.
 * @param onSearch called with [value] when the keyboard's search action is pressed.
 * @param clearContentDescription read out for the clear button.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onSearch: ((String) -> Unit)? = null,
    clearContentDescription: String = "Clear",
) {
    val colors = RisoTheme.colors
    val dimens = RisoTheme.dimens
    val textStyle = RisoTheme.typography.body.copy(color = colors.content)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusedBorder = RisoMix(
        colors.content to .5f,
        colors.accent to 1f,
        unprinted = colors.accent,
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorder.color() else colors.content,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) dimens.lineWidth * 2 else dimens.lineWidth,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
    )

    BasicTextField(
        value = value,
        // `singleLine` stops Enter from adding a line, but not a paste that carries one.
        onValueChange = { onValueChange(it.withoutLineBreaks()) },
        modifier = modifier
            // The same drums as the focused border's mix, so the plain border prints on them too.
            .risoInk(focusedBorder)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RisoTheme.shapes.standardShape,
            )
            .heightIn(min = MinHeight),
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(value) }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                // The clear button brings its own touch padding, so the end needs less.
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(type = IconType.Search)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            textStyle = textStyle,
                            color = colors.content.copy(alpha = PlaceholderAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    FlatButton(
                        icon = IconType.Close,
                        contentDescription = clearContentDescription,
                        onClick = { onValueChange("") },
                    )
                }
            }
        },
    )
}

private val LineBreak = Regex("\r\n|\r|\n")

internal fun String.withoutLineBreaks(): String = replace(LineBreak, " ")

@Preview
@Composable
private fun SearchFieldPreview() {
    RisoTheme {
        Column(
            Modifier.fillMaxSize()
                .risoPaper()
                .safeDrawingPadding()
                .padding(RisoTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var query by remember { mutableStateOf("") }
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search",
                modifier = Modifier.fillMaxWidth(),
            )
            SearchField(
                value = "The Left Hand of Darkness",
                onValueChange = {},
                placeholder = "Search",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
