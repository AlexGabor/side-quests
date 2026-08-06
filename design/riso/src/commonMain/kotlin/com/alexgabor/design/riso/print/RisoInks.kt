package com.alexgabor.design.riso.print

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * The number of drums the shader runs. Inks past this many in [RisoPrintParams.inks] are ignored.
 */
const val MAX_INKS = 3

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
 */
fun risoOverprint(paper: Color, inks: List<RisoInk>, coverages: List<Float>): Color {
    var red = paper.red
    var green = paper.green
    var blue = paper.blue
    inks.take(MAX_INKS).forEachIndexed { index, ink ->
        val coverage = coverages.getOrElse(index) { 0f }.coerceIn(0f, 1f)
        if (coverage > 0f) {
            red *= ink.color.red.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            green *= ink.color.green.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
            blue *= ink.color.blue.coerceAtLeast(MIN_TRANSMITTANCE).pow(coverage)
        }
    }
    return Color(red, green, blue)
}
