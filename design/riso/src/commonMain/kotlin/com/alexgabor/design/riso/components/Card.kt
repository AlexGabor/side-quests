package com.alexgabor.design.riso.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.pass.risoInk
import com.alexgabor.design.riso.print.risoOverprint

@Composable
fun Card(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedColor by animateColorAsState(
        targetValue = if (selected) getSelectedColor() else RisoTheme.colors.content
    )
    Box(
        modifier = modifier
            // The two drums the border is mixed from, so a selected card separates back onto exactly
            // the inks getSelectedColor() overprinted rather than onto whatever is nearest.
            .risoInk(RisoTheme.colors.content, RisoTheme.colors.accent)
            .border(
                width = RisoTheme.dimens.lineWidth * if (selected) 2 else 1,
                color = animatedColor,
                shape = RisoTheme.shapes.standardShape,
            ),
        content = content,
    )
}

@Composable
fun getSelectedColor() = risoOverprint(
    inks = arrayOf(RisoTheme.colors.content to .5f, RisoTheme.colors.accent to 1f),
)