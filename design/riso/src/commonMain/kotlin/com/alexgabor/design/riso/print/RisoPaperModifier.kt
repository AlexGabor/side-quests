package com.alexgabor.design.riso.print

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * The stock is painted behind the content, its surface shades what is printed on it, and its
 * unevenness pushes the artwork around the way a real sheet does. What lands on the sheet is the
 * business of [risoInk][com.alexgabor.design.riso.pass.risoInk], which runs the artwork through one
 * drum at a time; anything not put on a drum prints flat.
 *
 * The two halves are deliberately separate. The sheet is per-call, because a screen is printed on
 * one; the press — which drums are loaded, how hard they screen and mottle — is house style and
 * lives on [RisoTheme.press][com.alexgabor.design.riso.RisoTheme.press].
 *
 * ### Authoring
 * Inks laid on the same pixel **multiply**, so overlaps go dark exactly as ink on paper does. Where
 * a specific overprint or tint is wanted, [risoOverprint] gives the colour to draw so that it
 * separates back into precisely those coverages.
 *
 * There is no point drawing the stock colour yourself: the sheet is already there, and anything
 * within [Press.tolerance][com.alexgabor.design.riso.attributes.Press.tolerance] of it comes off the
 * press unprinted.
 */
expect fun Modifier.risoPaper(paper: RisoPaper = RisoPaper()): Modifier

/**
 * One ink pass: the drum colour, how far off-register that pass lands, and the angle of its halftone
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
 * The sheet the press prints onto: the colour of the stock, and the surface that colour sits on.
 *
 * The surface is procedural and static for a given stock and layout, so it is baked once and shared
 * — changing an ink never re-bakes it. It does three things to the print: it pushes the artwork
 * around by a fraction of a pixel, it shades whatever is printed on it, and it shows through
 * wherever nothing was printed.
 *
 * Ported from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture).
 */
data class RisoPaper(
    /** The stock's own colour. */
    val colorFront: Color = RisoColors.paper,
    /** What shows through the sheet where its surface lets light past. */
    val colorBack: Color = Color(0xFFFFFFFF),
    /** Sharper vs smoother transitions across the surface (0..1). */
    val contrast: Float = 0.12f,
    /** Pixel noise intensity (0..1). */
    val roughness: Float = 0.12f,
    /** Curly-shaped fiber noise intensity (0..1). */
    val fiber: Float = 0.1f,
    /** Curly-shaped fiber noise scale (0..1). */
    val fiberSize: Float = 0.29f,
    /** Big-scale noise mask applied to the surface (0..1). */
    val fade: Float = 0.5f,
    /**
     * Seed for the fade mask. Distinct from
     * [Press.seed][com.alexgabor.design.riso.attributes.Press.seed], which seeds the ink.
     */
    val seed: Float = 5.8f,
    /** Overall zoom level of the surface (0.01..4). */
    val scale: Float = 0.1f,
) {
    companion object {
        /**
         * No stock at all: nothing is painted behind the ink, so the print comes off the press as it
         * would with no sheet to print onto. The passes still multiply against each other, so what
         * is left is the inks' own transmittance — ink as if held up to the light.
         */
        val None = RisoPaper(
            // The alpha is what says the sheet is not painted.
            colorFront = Color(0x00FFFFFF),
            colorBack = Color(0x00FFFFFF),
            contrast = 0f,
            roughness = 0f,
            fiber = 0f,
            fade = 0f,
        )
    }
}
