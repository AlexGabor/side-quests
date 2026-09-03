package com.alexgabor.design.riso.risograph.paper

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * The paper is drawn behind the content, its surface shades what is printed on it, and its
 * unevenness pushes the artwork around the way a real sheet does.
 *
 * With the press stood down — `effectsEnabled = false` on the
 * [theme][com.alexgabor.design.riso.RisoTheme] — the stock is painted and nothing else: its color,
 * flat, with no surface to shade or warp what sits on it. Which also means no effect layer, so a
 * [risoBypass][com.alexgabor.design.riso.risograph.region.risoBypass] inside has nothing to report to
 * and, as it already documents, does nothing.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoPaper(paper: RisoPaper = RisoPaper()): Modifier =
    if (LocalRisoEffectsEnabled.current) risoPaperEffect(paper)
    // The stock's own color, and [RisoPaper.None]'s is transparent — so no sheet still means no
    // sheet here, and nothing is painted behind the ink.
    else background(paper.colorFront)

/** [risoPaper] with the press running: the sheet as this platform can put one down. */
internal expect fun Modifier.risoPaperEffect(paper: RisoPaper): Modifier

/**
 * The sheet the press prints onto: the color of the stock, and the surface that color sits on.
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
    /** The stock's own color. */
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
