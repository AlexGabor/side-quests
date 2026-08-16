package com.alexgabor.design.riso.risograph.inks

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.ln

/** The color as a per-channel transmittance, i.e. what full coverage of it does to white paper. */
internal fun Color.transmittance() = floatArrayOf(
    red.coerceIn(MIN_TRANSMITTANCE, 1f),
    green.coerceIn(MIN_TRANSMITTANCE, 1f),
    blue.coerceIn(MIN_TRANSMITTANCE, 1f),
)

/** Optical density of full coverage of [color], i.e. `-ln(transmittance)`. */
internal fun densityOf(color: Color): FloatArray {
    val t = color.transmittance()
    return floatArrayOf(-ln(t[0]), -ln(t[1]), -ln(t[2]))
}

internal fun dot3(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/**
 * Builds the color separation as one row vector per ink, such that ink `i`'s coverage at a pixel is
 * `dot(row[i], density)` where `density = -ln(pixel / paper)`.
 *
 * Densities add when inks stack, so recovering the coverages is a least-squares fit of the pixel's
 * density against the ink densities. The rows are the pseudo-inverse of that 3xN system, which
 * depends only on the ink colors and so is solved once here rather than per pixel.
 *
 * This is only ever asked about the one to three drums a composable named, where the system is small
 * and — at three — exact. It used to be asked about the whole rack, which is where it got into
 * trouble: three channels cannot pin down twelve unknowns, and the minimum-norm answer prefers to
 * wash a color thinly over every drum loaded rather than pick the few that can print it. Everything
 * that existed to paper over that (the hue fan, its wedges, the anchor ink) is gone, because a
 * composable now says which drums it prints on instead of leaving it to be inferred from a pixel.
 */
internal fun separationRows(inks: List<Color>): List<FloatArray> {
    if (inks.isEmpty()) return emptyList()
    val densities = inks.map(::densityOf)
    val n = densities.size

    // Normal equations of the fit, D^T D.
    val normal = Array(n) { i -> FloatArray(n) { j -> dot3(densities[i], densities[j]) } }

    // A ridge term proportional to the system's own scale, so that near-collinear inks (two blues,
    // say) share coverage between them instead of the inverse blowing up. It is small enough that a
    // well-separated palette still round-trips to within half a percent.
    var trace = 0f
    repeat(n) { trace += normal[it][it] }
    val ridge = 1e-3f * trace / n
    repeat(n) { normal[it][it] += ridge }

    val inverse = invert(normal) ?: return inks.map { FloatArray(3) }
    return List(n) { i ->
        FloatArray(3) { channel ->
            var sum = 0f
            repeat(n) { j -> sum += inverse[i][j] * densities[j][channel] }
            sum
        }
    }
}

/** Gauss-Jordan inverse of a small square matrix, or null if it is singular. */
private fun invert(matrix: Array<FloatArray>): Array<FloatArray>? {
    val n = matrix.size
    // Augment with the identity and reduce the left half to it.
    val work = Array(n) { i ->
        FloatArray(2 * n).also { row ->
            matrix[i].copyInto(row)
            row[n + i] = 1f
        }
    }
    for (col in 0 until n) {
        var pivot = col
        for (row in col + 1 until n) {
            if (abs(work[row][col]) > abs(work[pivot][col])) pivot = row
        }
        if (abs(work[pivot][col]) < 1e-6f) return null
        work[col] = work[pivot].also { work[pivot] = work[col] }

        val scale = work[col][col]
        for (k in 0 until 2 * n) work[col][k] /= scale
        for (row in 0 until n) {
            if (row == col) continue
            val factor = work[row][col]
            if (factor != 0f) {
                for (k in 0 until 2 * n) work[row][k] -= factor * work[col][k]
            }
        }
    }
    return Array(n) { i -> FloatArray(n) { j -> work[i][n + j] } }
}
