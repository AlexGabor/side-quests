package com.alexgabor.design.riso.risograph.paper

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * The stock is painted behind the content, with the surface of the sheet it is cut from. What is
 * drawn on it is left alone: only what is printed with
 * [risoInk][com.alexgabor.design.riso.risograph.inks.risoInk] lands on the paper as ink does —
 * multiplied onto the stock, separated against this sheet's own color, and pushed around by its
 * surface. An image, a plain fill, anything not inked, sits on top of the sheet exactly as drawn.
 *
 * Any stock color works, and changing one costs nothing: the surface is baked once per shape of
 * sheet, never per color, size or strength. A sheet inside another paints its own stock over the
 * outer one, and the ink inside it prints onto that.
 *
 * With the press stood down — `effectsEnabled = false` on the
 * [theme][com.alexgabor.design.riso.RisoTheme] — the stock is painted and nothing else: its color,
 * flat.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoPaper(paper: RisoPaper = RisoPaper()): Modifier =
    if (LocalRisoEffectsEnabled.current) this then RisoSheetElement(paper, tileCacheDir())
    // The stock's own color, and [RisoPaper.None]'s is transparent — so no sheet still means no
    // sheet here, and nothing is painted behind the ink.
    else background(paper.colorFront)

/**
 * Whether this stock's surface has landed at the current density, so that a frame drawn now shows it.
 *
 * A sheet's surface is loaded off the frame the first time anything asks for it, and until it lands
 * the stock is drawn flat — at its own average, so nothing shifts when the grain appears. On a
 * screen that is right: the grain simply arrives a moment later. It is wrong for anything that
 * captures a single frame, which is what this is for: reading it starts the load if nothing has, and
 * recomposes the caller when it lands, so a capture can wait for `true`.
 *
 * Also true once loading has given up — the sheet is then drawn flat for good, and there is nothing
 * more to wait for. Always true for a stock with no relief, and with the press stood down.
 */
@Composable
fun RisoPaper.isSurfaceReady(): Boolean {
    if (!LocalRisoEffectsEnabled.current || !warps) return true
    val density = LocalDensity.current.density
    val cacheDir = tileCacheDir()
    val fine = PaperTiles.get(fineTileKey(density), cacheDir)
    val coarse = PaperTiles.get(coarseTileKey(), cacheDir)
    var ready by remember(fine, coarse) { mutableStateOf(fine.settled && coarse.settled) }
    LaunchedEffect(fine, coarse) {
        fine.await()
        coarse.await()
        ready = true
    }
    return ready
}

/**
 * The sheet the press prints onto: the color of the stock, and the surface that color sits on.
 *
 * The surface is procedural and static, so it is baked once into tiles that repeat across the sheet
 * and are shared by every sheet of the same shape. It does three things to the print: it shades the
 * stock, it pushes the ink around by a fraction of a dp, and it shows through wherever nothing was
 * printed. Colors, [contrast], [roughness], [fiber] and [fade] are applied as the sheet is drawn, so
 * changing any of them never re-bakes; [fiberSize], [scale] and [seed] decide the tiles themselves.
 *
 * Ported from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture).
 */
data class RisoPaper(
    /** The stock's own color. */
    val colorFront: Color = RisoColors.paper,
    /** What shows through the sheet where its surface lets light past. */
    val colorBack: Color = RisoColors.paper,
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
         * would with no sheet to print onto — ink as if held up to the light.
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

/** Whether the stock's surface has any relief at all — anything to shade by, or to push the ink around. */
internal val RisoPaper.warps: Boolean get() = roughness > 0f || fiber > 0f

/** Whether any stock is painted. [RisoPaper.None] paints none. */
internal val RisoPaper.painted: Boolean get() = colorFront.alpha > 0f || colorBack.alpha > 0f

/**
 * The color ink printed on this stock is separated against: the stock as it looks with no relief,
 * which is also its average. Null for a stock that paints nothing, whose ink then separates against
 * the theme's paper as though the sheet were not there.
 */
internal val RisoPaper.separationColor: Color?
    get() {
        if (!painted) return null
        val front = colorFront.alpha * FLAT_LIGHTING
        val opacity = front + colorBack.alpha * (1f - front)
        fun channel(f: Float, b: Float) =
            ((f * front + b * colorBack.alpha * (1f - front)) / opacity).coerceIn(0f, 1f)
        return Color(
            red = channel(colorFront.red, colorBack.red),
            green = channel(colorFront.green, colorBack.green),
            blue = channel(colorFront.blue, colorBack.blue),
        )
    }
