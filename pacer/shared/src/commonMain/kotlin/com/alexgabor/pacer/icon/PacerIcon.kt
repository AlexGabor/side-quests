package com.alexgabor.pacer.icon

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
import com.alexgabor.design.riso.print.RisoPaper
import com.alexgabor.design.riso.print.RisoPrintParams
import com.alexgabor.design.riso.print.onRisoPaper
import com.alexgabor.design.riso.print.risoInkForSlot

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

/** The contour's colour as authored: exactly what a full pass of the vintage black drum lays down. */
val PacerIconContour: Color = RisoColors.inks.vintageBlack.onRisoPaper()

/** The splash's colour as authored: a full pass of the purple drum. */
val PacerIconSplash: Color = RisoColors.inks.purple.onRisoPaper()

/**
 * The stock the icon prints on.
 *
 * The default sheet takes most of its colour from a white backing — on a screenful of app that reads
 * as bright paper, but a launcher icon is mostly stock, and at 48 dp against a wallpaper it would
 * read as a white tile. Backing it with a warmer, deeper tone lands the sheet on actual paper while
 * still leaving the front and back far enough apart for the surface's lighting to show between them.
 */
val PacerIconPaper: RisoPaper = RisoPaper(colorBack = Color(0xFFE2DBCB))

/**
 * How the mark is printed.
 *
 * The full rack is loaded rather than just the two drums the artwork calls for: the separation only
 * fans a colour onto three drums at a time when it has at least three to choose from, and the fan is
 * what makes an authored colour separate back onto exactly the drum it came from. With the rack
 * loaded, the mark still prints on vintage black and purple alone — those are the only drums its
 * colours reach for — but it does so the way the running app would print it.
 *
 * Two departures from the defaults, both because an icon is seen at 48 dp:
 *  - `overprint` is well below the default, so where the contour crosses the splash the passes stack
 *    and go dark. At the default the two would meet at nearly the same luminance and the case would
 *    disappear into the disc.
 *  - the registration error is scaled down to about half a dp. The splash is already a deliberate
 *    misregistration by hand; the passes only need to whisper underneath it.
 *
 * The blotching is also lighter than the default. Mottle and grain both work by *subtracting*
 * coverage, so a rack that looks characterful across a screenful of artwork leaves a 26 dp contour
 * printing at about three quarters and reading grey rather than black.
 */
val PacerIconPrint: RisoPrintParams = RisoPrintParams(
    paper = PacerIconPaper,
    inks = RisoColors.inks.all.mapIndexed { slot, ink ->
        val drum = risoInkForSlot(slot, ink.color)
        drum.copy(
            offsetX = drum.offsetX * REGISTRATION_SCALE,
            offsetY = drum.offsetY * REGISTRATION_SCALE,
        )
    },
    overprint = 0.28f,
    mottle = 0.15f,
    mottleSize = 10f,
    grain = 0.08f,
    grainSize = 0.9f,
    wobble = 0.2f,
    spread = 0.25f,
)

/**
 * Radius of an adaptive icon's guaranteed-visible circle, as a fraction of the layer's side: the
 * inner 66 of 108 dp. Nothing outside it is safe from a launcher's mask.
 */
const val PACER_ICON_SAFE_RADIUS: Float = 66f / 108f / 2f

private const val REGISTRATION_SCALE = 0.45f
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
