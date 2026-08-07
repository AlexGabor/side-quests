package com.alexgabor.design.riso.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.print.risoOverprint

@Composable
fun Card(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedColor by animateColorAsState(
        targetValue = if (selected) getSelectedColor() else getNormalColor()
    )
    Box(
        modifier = modifier
            .border(
                width = RisoTheme.dimens.lineWidth,
                color = animatedColor,
                shape = RisoTheme.shapes.standardShape,
            ),
        content = content,
    )
}


@Composable
fun getNormalColor() = RisoTheme.colors.content

@Composable
fun getSelectedColor() = risoOverprint(
    inks = listOf(
        RisoTheme.colors.content,
        RisoTheme.colors.accent,
    ),
    coverages = listOf(
        .3f, 1f,
    )
)