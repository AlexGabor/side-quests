package com.alexgabor.stamp.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.alexgabor.design.riso.components.ButtonGroupItem

/**
 * An app whose icon the press prints.
 *
 * Every mark is built the same way — a contour on the vintage black drum over a splash on one
 * accent drum, kicked down and to the right as a second pass that did not quite line up with the
 * first — so the layers in [IconLayer] need nothing from a mark but its two inks and [drawMark].
 */
enum class IconMark(
    override val text: String,
    /** Where an export writes this mark's prints, named for the app they are copied into. */
    val dir: String,
    /** The contour's ink as authored: exactly what a full pass of its drum lays down. */
    val contour: Color,
    /** The splash's ink as authored. */
    val splash: Color,
) : ButtonGroupItem {
    Pacer("pacer", "pacer", PacerIconContour, PacerIconSplash),
    Bookplate("bookplate", "bookplate", BookplateIconContour, BookplateIconSplash),
}

/**
 * [mark] as artwork, fitted to [fitRadius].
 *
 * The splash is drawn first, on bare paper, and the contour prints over it with [contourBlend]:
 * multiplying is what makes the overlap a third colour instead of the contour hiding the splash —
 * see risoPrint's note on authoring overlaps subtractively.
 */
fun DrawScope.drawMark(
    mark: IconMark,
    contour: Color = mark.contour,
    splash: Color = mark.splash,
    contourBlend: BlendMode = BlendMode.Multiply,
    fitRadius: Float = ADAPTIVE_ICON_SAFE_RADIUS,
) = when (mark) {
    IconMark.Pacer -> drawPacerStopwatch(contour, splash, contourBlend, fitRadius)
    IconMark.Bookplate -> drawBookplateBook(contour, splash, contourBlend, fitRadius)
}

/**
 * [mark] as a flat silhouette, for surfaces that tint it themselves — Android's themed icons key off
 * alpha alone, so grain and misregistration would only punch holes in it.
 *
 * The splash survives as a wash rather than a second colour, which keeps the mark from reading as a
 * bare outline once the system has flattened it to one hue.
 */
fun DrawScope.drawMarkSilhouette(mark: IconMark, color: Color = Color.Black) = drawMark(
    mark = mark,
    contour = color,
    splash = color.copy(alpha = SILHOUETTE_SPLASH_ALPHA),
    contourBlend = BlendMode.SrcOver,
)

/**
 * Where [mark]'s ink ends up, filled solid: each drum's share of the artwork, moved to where that
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
fun DrawScope.drawMarkMask(
    mark: IconMark,
    contourOffset: Offset = Offset.Zero,
    splashOffset: Offset = Offset.Zero,
    color: Color = Color.Black,
) {
    translate(splashOffset.x, splashOffset.y) {
        drawMark(
            mark = mark,
            contour = Color.Transparent,
            splash = color,
            contourBlend = BlendMode.SrcOver,
        )
    }
    translate(contourOffset.x, contourOffset.y) {
        drawMark(
            mark = mark,
            contour = color,
            splash = Color.Transparent,
            contourBlend = BlendMode.SrcOver,
        )
    }
}

/**
 * Radius of an adaptive icon's guaranteed-visible circle, as a fraction of the layer's side: the
 * inner 66 of 108 dp. Nothing outside it is safe from a launcher's mask.
 */
const val ADAPTIVE_ICON_SAFE_RADIUS: Float = 66f / 108f / 2f

/**
 * How much of an iOS icon a mark fills, in the same fraction as [ADAPTIVE_ICON_SAFE_RADIUS].
 *
 * iOS masks with a squircle, which takes only the corners — the circle inscribed in the canvas
 * survives it whole. So the allowance [ADAPTIVE_ICON_SAFE_RADIUS] makes for a launcher's circular
 * mask buys nothing here and would leave the mark swimming. This is margin rather than safety: it
 * keeps a tenth of the canvas clear on every side, which is about where Apple's own marks sit in the
 * grid.
 */
const val IOS_ICON_FIT_RADIUS: Float = 0.40f

private const val SILHOUETTE_SPLASH_ALPHA = 0.28f
