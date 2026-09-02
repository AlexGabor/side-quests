package com.alexgabor.design.riso.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.Res
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.back_icon
import com.alexgabor.design.riso.settings_icon
import org.jetbrains.compose.resources.painterResource

enum class IconType {
    Back,
    Settings,
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
fun Icon(
    type: IconType,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = RisoTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = remember { MutableStyleState(interactionSource) }

    Image(
        painter = painterResource(type.toRes()),
        colorFilter = ColorFilter.tint(color = RisoTheme.colors.content),
        contentDescription = null,
        modifier = modifier.size(32.dp)
            .clickable(
                onClick = { onClick?.invoke() },
                enabled = onClick != null,
                interactionSource = interactionSource,
                indication = null,
            )
            .styleable(styleState) {
                hovered {
                    background(colors.content.copy(alpha = 0.25f))
                }
            },
    )
}

private fun IconType.toRes() = when (this) {
    IconType.Back -> Res.drawable.back_icon
    IconType.Settings -> Res.drawable.settings_icon
}