package com.alexgabor.design.riso.risograph.inks

import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.RisoColors
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * One ink pass: the drum color, how far off-register that pass lands, and the angle of its halftone
 * screen.
 */
data class RisoInk(
    val color: Color,
    /** Horizontal registration error of this pass, in dp. */
    val offsetX: Float = 0f,
    /** Vertical registration error of this pass, in dp. */
    val offsetY: Float = 0f,
    /** Halftone screen angle in degrees. Keep passes ~30 degrees apart to avoid moire. */
    val screenAngle: Float = 45f,
)

/**
 * The drum a press would load in slot [slot], carrying [color].
 *
 * No two presses register alike, so the error is spun around the slot index rather than tabulated:
 * each drum lands a few dp off in its own direction, and successive screen angles sit far enough
 * apart that neighboring passes do not moire.
 */
internal fun risoInkForSlot(slot: Int, color: Color): RisoInk = RisoInk(
    color = color,
    offsetX = 3f * cos(slot * 2.4f),
    offsetY = 3f * sin(slot * 2.4f),
    screenAngle = (15f + slot * 37.5f) % 90f,
)

/**
 * Lower bound on a transmittance. A channel that transmits nothing has infinite optical density, so
 * pure black is treated as a very dark — but finite — ink. The separation, the shader and
 * [risoOverprint] all floor at this same value; if they disagreed, the darkest colors would
 * separate into more ink than the inks themselves can lay down.
 */
internal const val MIN_TRANSMITTANCE = 0.02f

/**
 * The color to draw so that a [risoInk][risoInk] naming these same
 * inks lays down exactly [coverages] of them on [paper] —
 * `coverages[i]` being the fraction of drum `i`, as on a press's tint scale.
 *
 * Densities add as ink stacks, so a coverage `c` of
 * an ink transmits `ink^c` (Beer-Lambert), and the color to author is the paper seen through all
 * of them. Use it for tint ramps and overprint charts, where eyeballing a blend would land on a
 * color that separates back into something else entirely.
 *
 * The color always comes back off the press as authored. Which drums print it is another matter:
 * one ink, or the black drum with one other, separate back onto exactly the drums given here, but a
 * mix of two inks far apart on the color wheel is indistinguishable from a mix of the inks that sit
 * between them, and the press will reach for whichever of the two it can print in one wedge.
 */
fun risoOverprint(paper: Color = RisoColors.paper, vararg inks: Pair<Color, Float>): Color {
    var red = paper.red
    var green = paper.green
    var blue = paper.blue
    inks.forEach { (ink, coverage) ->
        val coverage = coverage.coerceIn(0f, 1f)
        if (coverage > 0f) {
            red *= ink.red.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            green *= ink.green.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            blue *= ink.blue.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
        }
    }
    return Color(red, green, blue)
}

fun Color.onRisoPaper(inkCoverage: Float = 1f, paper: Color = RisoColors.paper): Color {
    val coverage = inkCoverage.coerceIn(0f, 1f)
    if (coverage == 0f) return paper
    return Color(
        red = paper.red * this.red.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage),
        green = paper.green * this.green.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage),
        blue = paper.blue * this.blue.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage),
    )
}