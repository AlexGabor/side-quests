package com.alexgabor.design.riso.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Caps this content at [max] inside a container [available] wide, and consumes the window insets the
 * side gaps already cover, so a child that pads for `safeDrawing` pads only for the part of an inset
 * that actually reaches it.
 *
 * The gap comes from the space the container was given, not from where the content lands in the
 * window. `recalculateWindowInsets` reads its position in the window on every layout pass, so a
 * navigation transition that slides a screen across the window makes it consume most of a screen
 * width and reshapes the content mid-flight.
 *
 * An unbounded parent leaves [available] at [Dp.Infinity], which leaves the insets alone.
 */
fun Modifier.contentWidth(available: Dp, max: Dp): Modifier {
    val gap = if (available == Dp.Infinity) 0.dp else ((available - max) / 2).coerceAtLeast(0.dp)
    return widthIn(max = max).consumeWindowInsets(PaddingValues(horizontal = gap))
}
