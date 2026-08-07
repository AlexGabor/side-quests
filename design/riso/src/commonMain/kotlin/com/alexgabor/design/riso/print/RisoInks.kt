package com.alexgabor.design.riso.print

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The drum a press would load in slot [slot], carrying [color].
 *
 * No two presses register alike, so the error is spun around the slot index rather than tabulated:
 * each drum lands about a dp off in its own direction, and successive screen angles sit far enough
 * apart that neighbouring passes do not moire.
 */
fun risoInkForSlot(slot: Int, color: Color): RisoInk = RisoInk(
    color = color,
    offsetX = 1.2f * cos(slot * 2.4f),
    offsetY = 1.2f * sin(slot * 2.4f),
    screenAngle = (15f + slot * 37.5f) % 90f,
)

/**
 * Lower bound on a transmittance. A channel that transmits nothing has infinite optical density, so
 * pure black is treated as a very dark — but finite — ink. The separation, the shader and
 * [risoOverprint] all floor at this same value; if they disagreed, the darkest colours would
 * separate into more ink than the inks themselves can lay down.
 */
internal const val MIN_TRANSMITTANCE = 0.02f

/**
 * The colour to draw so that [risoPrint] lays down exactly [coverages] of [inks] on [paper] —
 * `coverages[i]` being the fraction of drum `i`, as on a press's tint scale.
 *
 * This is the inverse of the shader's separation. Densities add as ink stacks, so a coverage `c` of
 * an ink transmits `ink^c` (Beer-Lambert), and the colour to author is the paper seen through all
 * of them. Use it for tint ramps and overprint charts, where eyeballing a blend would land on a
 * colour that separates back into something else entirely.
 *
 * The colour always comes back off the press as authored. Which drums print it is another matter:
 * one ink, or the black drum with one other, separate back onto exactly the drums given here, but a
 * mix of two inks far apart on the colour wheel is indistinguishable from a mix of the inks that sit
 * between them, and the press will reach for whichever of the two it can print in one wedge.
 */
fun risoOverprint(paper: Color = Color(0x00FFFFFF), inks: List<Color>, coverages: List<Float>): Color {
    var red = paper.red
    var green = paper.green
    var blue = paper.blue
    inks.forEachIndexed { index, ink ->
        val coverage = coverages.getOrElse(index) { 0f }.coerceIn(0f, 1f)
        if (coverage > 0f) {
            red *= ink.red.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            green *= ink.green.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            blue *= ink.blue.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
        }
    }
    return Color(red, green, blue)
}
