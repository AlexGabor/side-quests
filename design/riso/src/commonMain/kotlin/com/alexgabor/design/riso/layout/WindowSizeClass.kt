package com.alexgabor.design.riso.layout

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthSizeClass {
    Compact, Medium, Expanded,
}

enum class WindowHeightSizeClass {
    Compact, Medium, Expanded,
}

/**
 * The window breakpoints from
 * [the adaptive apps guide](https://developer.android.com/develop/adaptive-apps), expressed as a
 * pair of buckets so a layout can branch on the space it was given rather than on the device it
 * happens to be running on.
 */
@Immutable
data class WindowSizeClass(
    val width: WindowWidthSizeClass,
    val height: WindowHeightSizeClass,
) {
    companion object {
        val MediumWidthLowerBound: Dp = 600.dp
        val ExpandedWidthLowerBound: Dp = 840.dp
        val MediumHeightLowerBound: Dp = 480.dp
        val ExpandedHeightLowerBound: Dp = 900.dp

        fun compute(width: Dp, height: Dp): WindowSizeClass = WindowSizeClass(
            width = when {
                width >= ExpandedWidthLowerBound -> WindowWidthSizeClass.Expanded
                width >= MediumWidthLowerBound -> WindowWidthSizeClass.Medium
                else -> WindowWidthSizeClass.Compact
            },
            height = when {
                height >= ExpandedHeightLowerBound -> WindowHeightSizeClass.Expanded
                height >= MediumHeightLowerBound -> WindowHeightSizeClass.Medium
                else -> WindowHeightSizeClass.Compact
            },
        )
    }
}

/**
 * The size class of the space this container was given — not of the physical device. Reading the
 * incoming constraints rather than the window keeps the layout correct under split screen, freeform
 * and desktop window drag, and makes `@Preview(widthDp = ..., heightDp = ...)` show what a window of
 * that size really renders.
 *
 * An unbounded parent leaves [maxWidth]/[maxHeight] at [Dp.Infinity], which falls through to
 * [WindowWidthSizeClass.Expanded]/[WindowHeightSizeClass.Expanded].
 */
fun BoxWithConstraintsScope.computeWindowSizeClass(): WindowSizeClass =
    WindowSizeClass.compute(maxWidth, maxHeight)
