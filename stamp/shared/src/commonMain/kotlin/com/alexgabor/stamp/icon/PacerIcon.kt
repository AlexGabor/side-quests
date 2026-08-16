package com.alexgabor.stamp.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Pacer's mark: a stopwatch on the vintage black drum, with a splash of purple kicked down and to
 * the right of it, as a second pass that did not quite line up with the first.
 *
 * Every length is a fraction of the canvas's shorter side, so the same drawing serves a 48 dp
 * composable and a 432 px launcher icon. The mark is then fitted to [fitRadius]: its own bounding
 * circle is moved onto the canvas centre and scaled to that radius, which by default is exactly the
 * circle an adaptive icon guarantees will survive a launcher's mask.
 */
fun DrawScope.drawPacerStopwatch(
    contour: Color = PacerIconContour,
    splash: Color = PacerIconSplash,
    contourBlend: BlendMode = BlendMode.Multiply,
    fitRadius: Float = PACER_ICON_SAFE_RADIUS,
) {
    val s = size.minDimension
    val origin = Offset((size.width - s) / 2f, (size.height - s) / 2f)
    fun at(x: Float, y: Float) = origin + Offset(x * s, y * s)

    val ringCenter = at(RING_CENTER_X, RING_CENTER_Y)
    val ringRadius = RING_RADIUS * s
    val stroke = Stroke(width = CONTOUR_WIDTH * s, cap = StrokeCap.Round)

    // Scaling about the canvas centre after shifting the mark's own centre onto it puts every point
    // at `(p - artCentre) * fit + canvasCentre`, which is the fit exactly.
    scale(fitRadius / ART_RADIUS, pivot = center) {
        translate((0.5f - ART_CENTER_X) * s, (0.5f - ART_CENTER_Y) * s) {
            // The splash lands first, on bare paper. Everything after it prints *over* what is
            // there: multiplying is what makes the overlap a third colour instead of the contour
            // hiding the disc — see risoPrint's note on authoring overlaps subtractively.
            drawCircle(
                color = splash,
                radius = SPLASH_RADIUS * s,
                center = at(SPLASH_CENTER_X, SPLASH_CENTER_Y),
                blendMode = BlendMode.SrcOver,
            )

            // The case, open at the upper right so the button has somewhere to sit.
            drawArc(
                color = contour,
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_SWEEP_ANGLE,
                useCenter = false,
                topLeft = ringCenter - Offset(ringRadius, ringRadius),
                size = Size(2f * ringRadius, 2f * ringRadius),
                style = stroke,
                blendMode = contourBlend,
            )

            // The crown, clear of the case: at icon size the two run together if they touch.
            drawRoundRect(
                color = contour,
                topLeft = at(RING_CENTER_X - CROWN_WIDTH / 2f, CROWN_CENTER_Y - CROWN_HEIGHT / 2f),
                size = Size(CROWN_WIDTH * s, CROWN_HEIGHT * s),
                cornerRadius = CornerRadius(CROWN_HEIGHT / 2f * s),
                blendMode = contourBlend,
            )

            // The side button, on the bisector of the gap and drawn with the case's own pen.
            drawLine(
                color = contour,
                start = at(BUTTON_START_X, BUTTON_START_Y),
                end = at(BUTTON_END_X, BUTTON_END_Y),
                strokeWidth = CONTOUR_WIDTH * s,
                cap = StrokeCap.Round,
                blendMode = contourBlend,
            )

            // The hand, stopped at twelve.
            drawLine(
                color = contour,
                start = ringCenter,
                end = at(RING_CENTER_X, NEEDLE_TIP_Y),
                strokeWidth = NEEDLE_WIDTH * s,
                cap = StrokeCap.Round,
                blendMode = contourBlend,
            )
            drawCircle(
                color = contour,
                radius = HUB_RADIUS * s,
                center = ringCenter,
                blendMode = contourBlend,
            )
        }
    }
}

/**
 * The same mark as a flat silhouette, for surfaces that tint it themselves — Android's themed icons
 * key off alpha alone, so grain and misregistration would only punch holes in it.
 *
 * The splash survives as a wash rather than a second colour, which keeps the mark from reading as a
 * bare outline once the system has flattened it to one hue.
 */
fun DrawScope.drawPacerSilhouette(color: Color = Color.Black) = drawPacerStopwatch(
    contour = color,
    splash = color.copy(alpha = SILHOUETTE_SPLASH_ALPHA),
    contourBlend = BlendMode.SrcOver,
)

/**
 * Where the mark's ink ends up, filled solid: each drum's share of the artwork, moved to where that
 * drum lays it down.
 *
 * This is what a print has to be cut back to before it can be composited. A press has no alpha
 * channel to hand over — a pass lays ink where the artwork calls for it and *white* everywhere else,
 * which is nothing at all under the multiply the passes are drawn with, but an opaque white square
 * the moment something asks the result to sit over a background.
 *
 * Which is also why the two shares are split and offset separately rather than the whole mark being
 * stamped once. Follow the artwork and the mask keeps the edge each pass has already moved away
 * from, leaving a rim of paper the drum never reached; follow the drums and the mask stops exactly
 * where the ink does. [contourOffset] and [splashOffset] are those two drums' registration errors,
 * in pixels, after whatever damping the caller printed with.
 */
fun DrawScope.drawPacerMask(
    contourOffset: Offset = Offset.Zero,
    splashOffset: Offset = Offset.Zero,
    color: Color = Color.Black,
) {
    translate(splashOffset.x, splashOffset.y) {
        drawPacerStopwatch(
            contour = Color.Transparent,
            splash = color,
            contourBlend = BlendMode.SrcOver,
        )
    }
    translate(contourOffset.x, contourOffset.y) {
        drawPacerStopwatch(
            contour = color,
            splash = Color.Transparent,
            contourBlend = BlendMode.SrcOver,
        )
    }
}

/** The contour's colour as authored: exactly what a full pass of the vintage black drum lays down. */
val PacerIconContour: Color = RisoColors.inks.vintageBlack

/** The splash's colour as authored: a full pass of the purple drum. */
val PacerIconSplash: Color = RisoColors.inks.purple

/**
 * Radius of an adaptive icon's guaranteed-visible circle, as a fraction of the layer's side: the
 * inner 66 of 108 dp. Nothing outside it is safe from a launcher's mask.
 */
const val PACER_ICON_SAFE_RADIUS: Float = 66f / 108f / 2f

private const val SILHOUETTE_SPLASH_ALPHA = 0.28f

// The mark's own bounding circle, in the same fractions as everything below. Recompute these two if
// any element moves outward, or the fit will crop.
private const val ART_CENTER_X = 0.5017f
private const val ART_CENTER_Y = 0.4804f
private const val ART_RADIUS = 0.3434f

private const val RING_CENTER_X = 0.455f
private const val RING_CENTER_Y = 0.480f
private const val RING_RADIUS = 0.225f
private const val CONTOUR_WIDTH = 0.058f

// Measured from three o'clock, clockwise on screen, so the gap is centred on the upper right at
// -45 degrees and is 52 degrees wide — wide enough that the button reads as sitting in it rather
// than as a piece that broke off the case.
private const val RING_START_ANGLE = -19f
private const val RING_SWEEP_ANGLE = 308f

private const val CROWN_CENTER_Y = 0.186f
private const val CROWN_WIDTH = 0.185f
private const val CROWN_HEIGHT = 0.058f

// On the gap's bisector, from just clear of the case out to the mark's widest point.
private const val BUTTON_START_X = 0.6282f
private const val BUTTON_START_Y = 0.3068f
private const val BUTTON_END_X = 0.6813f
private const val BUTTON_END_Y = 0.2537f

private const val NEEDLE_TIP_Y = 0.335f
private const val NEEDLE_WIDTH = 0.040f
private const val HUB_RADIUS = 0.036f

private const val SPLASH_CENTER_X = 0.532f
private const val SPLASH_CENTER_Y = 0.556f
private const val SPLASH_RADIUS = 0.262f
