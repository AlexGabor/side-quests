package com.alexgabor.design.riso.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper

/** How strongly the part still to go is printed, against the full accent of the part that's done. */
private const val RemainingAlpha = 0.5f

/**
 * A progress line as thin as the theme's rules: the part that is done in the accent, the rest in the
 * content color at half strength. Made for places with no room for a [Track][com.alexgabor.design.riso.components.track.Track],
 * like a list row.
 *
 * @param progress how much is done, from 0 to 1. Anything outside is clamped, and NaN counts as 0.
 */
@Composable
fun SmallProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = RisoTheme.colors
    val accent = colors.accent
    val remaining = colors.content.copy(alpha = RemainingAlpha)
    val fraction = progress.clampProgress()

    Box(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
            .height(3.dp)
            // The accent part separates onto its own drum and the half-strength content becomes a
            // half-tone on the black one, the way a press prints a tint. Printed in register: the
            // drums' usual 3 dp throw is wider than the line, and would part the two halves.
            .risoInk(accent, colors.content, offsetScale = 0f)
            .drawBehind {
                val done = size.width * fraction
                val doneStart = if (layoutDirection == LayoutDirection.Rtl) size.width - done else 0f
                val restStart = if (layoutDirection == LayoutDirection.Rtl) 0f else done
                drawRect(remaining, Offset(restStart, 0f), Size(size.width - done, size.height))
                drawRect(accent, Offset(doneStart, 0f), Size(done, size.height))
            },
    )
}

internal fun Float.clampProgress(): Float = if (isNaN()) 0f else coerceIn(0f, 1f)

@Preview
@Composable
private fun SmallProgressPreview() {
    RisoTheme {
        Column(
            Modifier.fillMaxSize()
                .risoPaper()
                .safeDrawingPadding()
                .padding(RisoTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SmallProgress(progress = 0f, modifier = Modifier.fillMaxWidth())
            SmallProgress(progress = 0.4f, modifier = Modifier.fillMaxWidth())
            SmallProgress(progress = 1f, modifier = Modifier.fillMaxWidth())
        }
    }
}
