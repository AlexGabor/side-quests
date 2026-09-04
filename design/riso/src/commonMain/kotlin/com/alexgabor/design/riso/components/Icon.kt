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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import com.alexgabor.design.riso.RisoTheme

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
    val dimens = RisoTheme.dimens
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = remember { MutableStyleState(interactionSource) }

    Image(
        painter = rememberVectorPainter(
            rememberIconVector(type, dimens.iconSize, dimens.lineWidth, colors.content)
        ),
        contentDescription = null,
        modifier = modifier.size(dimens.iconSize)
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

/**
 * The icon drawn at [size], stroked [lineWidth] wide.
 *
 * Built here rather than loaded from a drawable so that the stroke can be the theme's own — the same
 * rule the borders and the heading underlines are drawn with, so an icon carries the weight of
 * everything around it whatever [Dimens][com.alexgabor.design.riso.attributes.Dimens] says. A vector
 * on disk cannot read the theme, and would have to be matched to it by hand at one size only.
 */
@Composable
private fun rememberIconVector(
    type: IconType,
    size: Dp,
    lineWidth: Dp,
    color: Color,
): ImageVector = remember(type, size, lineWidth, color) {
    ImageVector.Builder(
        defaultWidth = size,
        defaultHeight = size,
        viewportWidth = IconViewport,
        viewportHeight = IconViewport,
    ).apply {
        type.paths().forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(color),
                strokeLineWidth = lineWidth.value * IconViewport / size.value,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

private fun IconType.paths() = when (this) {
    IconType.Back -> BackIconPaths
    IconType.Settings -> SettingsIconPaths
}
