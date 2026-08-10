package com.alexgabor.pacer.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.print.risoPrint

/**
 * The launcher icon as the press would hand it back, at the 108 dp of an adaptive layer.
 *
 * Geometry is worth settling here rather than in the bake: this recomposes as fast as any preview,
 * whereas checking a change through the export activity costs an install, a launch and a pull.
 */
@Preview
@Composable
private fun PacerIconPreview() {
    Box(Modifier.size(108.dp).risoPrint(PacerIconPrint)) {
        Canvas(Modifier.size(108.dp)) { drawPacerStopwatch() }
    }
}

/**
 * The same drawing with an adaptive icon's two boundaries laid over it: the 72 dp a launcher is
 * likely to show, and the 66 dp circle it is guaranteed not to crop. The mark has to clear the
 * inner one; anything between the two is at the launcher's mercy.
 */
@Preview
@Composable
private fun PacerIconSafeZonePreview() {
    Box(Modifier.size(108.dp)) {
        Canvas(Modifier.size(108.dp)) {
            drawPacerStopwatch()
            drawRect(
                color = Color.Red.copy(alpha = 0.5f),
                topLeft = center - Offset(size.width / 3f, size.height / 3f),
                size = Size(size.width * 2f / 3f, size.height * 2f / 3f),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = Color.Red,
                radius = PACER_ICON_SAFE_RADIUS * size.minDimension,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** The themed-icon layer: flat, untinted, and judged on its alpha alone. */
@Preview
@Composable
private fun PacerSilhouettePreview() {
    Canvas(Modifier.size(108.dp)) { drawPacerSilhouette() }
}
