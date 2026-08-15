package com.alexgabor.design.riso.pass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect

/**
 * One drum's pass over the artwork.
 *
 * A pass reads the artwork as recorded and hands back what that drum lays on the sheet: the ink where
 * the colour calls for it, and white — which is nothing at all, under the multiply the pass is drawn
 * with — where it does not. Everything about *where* the pass lands is the caller's business: the
 * registration error is a translation of the whole pass, not something the shader knows about.
 *
 * Held across draws rather than rebuilt, because the shader behind it is expensive to compile and
 * cheap to re-uniform.
 */
internal expect class InkPass() {

    /**
     * The effect to hang on this pass's layer, or null on a platform with no runtime shaders — where
     * the caller draws the artwork as it is and no ink is laid at all.
     */
    fun effect(spec: InkPassSpec): RenderEffect?
}

/**
 * Everything one pass needs, with every length already in pixels and every angle in radians.
 *
 * [row] is this drum's row of the separation over the drums the region named, so that coverage is
 * `dot(row, density)` — the same shape the old rack-wide separation produced, but solved over one to
 * three inks the author chose rather than fitted against a rack of twelve. That is the whole reason
 * the fan and its wedges are gone: over a named handful the system is small and exact.
 */
internal class InkPassSpec(
    /** The ink on the drum, as loaded — not as it prints once the stock is under it. */
    val ink: Color,
    /** This drum's row of the separation, three channels. */
    val row: FloatArray,
    /** The stock the artwork is separated against. Nothing lighter than this can be printed. */
    val paper: Color,
    /** How close to [paper] a colour has to be to come off the press unprinted (0..1). */
    val tolerance: Float,
    /**
     * Where this pass sits on the page, in pixels. The screen and the mottle belong to the press
     * rather than to the artwork, so they are read from here rather than from the pass's own origin.
     */
    val origin: Offset,
    val screenAngle: Float,
    /** Offsets this drum's mottling and speckle, so no two passes blotch in the same places. */
    val phase: Float,
    val screen: Float,
    val dotSize: Float,
    val mottle: Float,
    val mottleSize: Float,
    val grain: Float,
    val grainSize: Float,
    val spread: Float,
)
