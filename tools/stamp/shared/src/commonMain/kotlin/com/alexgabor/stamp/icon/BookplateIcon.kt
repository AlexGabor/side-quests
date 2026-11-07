package com.alexgabor.stamp.icon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Bookplate's mark: a closed book with a ribbon marking the place, on the vintage black drum, over a
 * slab of orange in the book's own shape, kicked down and to the right of it as Pacer's splash is.
 *
 * Laid out as [drawPacerStopwatch] is: every length a fraction of the canvas's shorter side, and the
 * mark's own bounding circle moved onto the canvas centre and scaled to [fitRadius].
 */
fun DrawScope.drawBookplateBook(
    contour: Color = BookplateIconContour,
    splash: Color = BookplateIconSplash,
    contourBlend: BlendMode = BlendMode.Multiply,
    fitRadius: Float = ADAPTIVE_ICON_SAFE_RADIUS,
) {
    val s = size.minDimension
    val origin = Offset((size.width - s) / 2f, (size.height - s) / 2f)
    fun at(x: Float, y: Float) = origin + Offset(x * s, y * s)

    scale(fitRadius / ART_RADIUS, pivot = center) {
        translate((0.5f - ART_CENTER_X) * s, (0.5f - ART_CENTER_Y) * s) {
            // The slab lands first, on bare paper: the book's whole silhouette, out to the outer edge
            // of the contour's pen, so it reads as the same book printed again rather than as a
            // panel behind it.
            val pen = CONTOUR_WIDTH / 2f
            val slabTopLeft = at(COVER_LEFT - pen + SPLASH_OFFSET, COVER_TOP - pen + SPLASH_OFFSET)
            val slabBottomRight =
                at(COVER_RIGHT + pen + SPLASH_OFFSET, COVER_BOTTOM + pen + SPLASH_OFFSET)
            drawPath(
                path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(slabTopLeft, slabBottomRight),
                            cornerRadius = CornerRadius((CORNER_RADIUS + pen) * s),
                        ),
                    )
                },
                color = splash,
                blendMode = BlendMode.SrcOver,
            )

            // The contour is several strokes that meet — the ribbon hangs off the cover's top edge,
            // the page edge runs into the spine's curl — so it is drawn flat into a layer of its own
            // and that layer is what prints over the slab. Multiplying each stroke separately would
            // ink every join twice, and the overlap would come out darker than one pass of black.
            drawIntoCanvas {
                it.saveLayer(
                    bounds = Rect(Offset.Zero, size).inflate(size.maxDimension),
                    paint = Paint().apply { blendMode = contourBlend },
                )
            }

            val stroke = Stroke(
                width = CONTOUR_WIDTH * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            // The cover, every corner rounded as the spine's curl is.
            drawPath(
                path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(at(COVER_LEFT, COVER_TOP), at(COVER_RIGHT, COVER_BOTTOM)),
                            cornerRadius = CornerRadius(CORNER_RADIUS * s),
                        ),
                    )
                },
                color = contour,
                style = stroke,
            )

            // The block of pages: the spine's curl carried round and on across the book, so the
            // bottom of the cover reads as page edges rather than as a second frame.
            drawPath(
                path = Path().apply {
                    moveTo(at(COVER_LEFT, COVER_BOTTOM - CORNER_RADIUS))
                    arcTo(
                        rect = Rect(
                            center = at(COVER_LEFT + CORNER_RADIUS, COVER_BOTTOM - CORNER_RADIUS),
                            radius = CORNER_RADIUS * s,
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    lineTo(at(COVER_RIGHT, COVER_BOTTOM - 2f * CORNER_RADIUS))
                },
                color = contour,
                style = stroke,
            )

            // The ribbon, hanging from the top edge with a notch cut in its tail. Filled, then traced
            // with a fine round pen so its corners are as soft as the cover's.
            val ribbon = Path().apply {
                moveTo(at(RIBBON_LEFT, COVER_TOP))
                lineTo(at(RIBBON_RIGHT, COVER_TOP))
                lineTo(at(RIBBON_RIGHT, RIBBON_TAIL_Y))
                lineTo(at((RIBBON_LEFT + RIBBON_RIGHT) / 2f, RIBBON_TAIL_Y - RIBBON_NOTCH))
                lineTo(at(RIBBON_LEFT, RIBBON_TAIL_Y))
                close()
            }
            drawPath(ribbon, color = contour)
            drawPath(
                path = ribbon,
                color = contour,
                style = Stroke(width = RIBBON_ROUNDING * s, join = StrokeJoin.Round),
            )

            drawIntoCanvas { it.restore() }
        }
    }
}

/** The contour's colour as authored: exactly what a full pass of the vintage black drum lays down. */
val BookplateIconContour: Color = RisoColors.inks.vintageBlack

/** The slab's colour as authored: a full pass of the orange drum. */
val BookplateIconSplash: Color = RisoColors.inks.orange

private fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)

private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)

// The mark's own bounding circle, in the same fractions as everything below: it runs from the
// cover's top-left corner to the slab's bottom-right one. Recompute these if either moves outward,
// or the fit will crop.
private const val ART_CENTER_X = 0.5275f
private const val ART_CENTER_Y = 0.5075f
private const val ART_RADIUS = 0.4046f

private const val CONTOUR_WIDTH = 0.058f

// The cover's stroke runs along these. Its corners are all rounded as the spine's curl is, and the
// curl is also half the height of the page block, so the gap between the page edge and the bottom of
// the cover is the curl's diameter less one pen width — wide enough to stay open at icon size.
private const val COVER_LEFT = 0.285f
private const val COVER_RIGHT = 0.695f
private const val COVER_TOP = 0.190f
private const val COVER_BOTTOM = 0.750f
private const val CORNER_RADIUS = 0.060f

// Right of centre, so it sits clear of the spine's curl and over the slab's overlap, and short of
// the top-right corner's arc, so it hangs from a straight edge.
private const val RIBBON_LEFT = 0.535f
private const val RIBBON_RIGHT = 0.615f
private const val RIBBON_TAIL_Y = 0.400f
private const val RIBBON_NOTCH = 0.045f
private const val RIBBON_ROUNDING = 0.024f

// How far the slab is kicked down and to the right — about as far as Pacer's disc.
private const val SPLASH_OFFSET = 0.075f
