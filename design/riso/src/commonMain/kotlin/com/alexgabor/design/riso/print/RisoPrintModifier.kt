package com.alexgabor.design.riso.print

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Re-prints whatever the composable draws as a Risograph print of as many spot colours as there are
 * drums in [RisoPrintParams.inks].
 *
 * The content is separated into one ink coverage map per drum, each pass is sampled with its own
 * registration error (the pink/blue fringes of a real riso), and the passes are recombined
 * subtractively so overlaps produce a new colour instead of one ink hiding the other.
 *
 * Because the separation is done in ink-density space, content authored in (or near) the ink
 * colours round-trips exactly; arbitrary colours are projected onto the inks available, which is
 * lossy in the same way sending an RGB artwork to a two-drum press is.
 *
 * The sheet it prints onto is part of the effect: [RisoPaper] paints the stock behind the ink and
 * gives it a surface, so the paper's fiber and roughness shade the print and push the artwork
 * around the way an uneven sheet does. Pass [RisoPaper.None] for the inks alone, on nothing.
 *
 * ### Authoring
 * The modifier only sees the composited result, so **overlaps have to be authored subtractively**
 * for there to be anything to overprint. Painting an opaque shape over another knocks the ink
 * underneath out of the artwork, exactly as a knockout does on a real press; draw the upper shape
 * with `BlendMode.Multiply` instead and the overlap arrives as a colour that separates into both
 * inks. For tints and specific overprints, [risoOverprint] gives you the exact colour to draw.
 *
 * There is no point drawing the stock colour yourself: the sheet is already there, and anything
 * within [RisoPaper.tolerance] of it comes off the press unprinted.
 */
expect fun Modifier.risoPrint(params: RisoPrintParams = RisoPrintParams()): Modifier

/**
 * One ink pass: the drum colour, how far off-register that pass lands, and the angle of its
 * halftone screen.
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
    /**
     * The stock's own colour. Doubles as the white point the inks are separated against: a press
     * cannot print white, so anything lighter than this reads as bare paper.
     */
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
    /** Seed for the fade mask. Distinct from [RisoPrintParams.seed], which seeds the ink. */
    val seed: Float = 5.8f,
    /** Overall zoom level of the surface (0.01..4). */
    val scale: Float = 0.1f,
    /**
     * How close to the stock a colour has to be to come off the press unprinted (0..1), as a
     * fraction darker than [colorFront]. The default swallows about two 8-bit levels, so artwork
     * authored to the stock colour — and the antialiased edges of anything drawn on it — reads as
     * bare paper rather than as a tint no press could hold.
     */
    val tolerance: Float = 0.01f,
) {
    companion object {
        /**
         * No stock at all: the inks separate against white and nothing is painted behind them, so
         * the print comes off the press exactly as it would with no sheet to print onto.
         */
        val None = RisoPaper(
            // The alpha is what says the sheet is not painted; the RGB is still the white point.
            colorFront = Color(0x00FFFFFF),
            colorBack = Color(0x00FFFFFF),
            contrast = 0f,
            roughness = 0f,
            fiber = 0f,
            fade = 0f,
            tolerance = 0f,
        )
    }
}

/** Parameters for [risoPrint]. */
data class RisoPrintParams(
    /** The sheet the inks are printed onto. */
    val paper: RisoPaper = RisoPaper(),
    /**
     * One entry per drum, in printing order. Loading the whole rack is close to free: a colour is
     * separated onto the few drums that can print it, and the rest are never sampled.
     */
    val inks: List<RisoInk> = RisoColors.inks.all.mapIndexed { slot, ink ->
        risoInkForSlot(slot, ink.color)
    },
    /**
     * How inks mix where they overlap (0..1).
     *
     * `0` is fully subtractive — the passes multiply, so overlaps go dark like a screen print.
     * `1` treats the dots as juxtaposed rather than stacked, averaging the inks, which is what
     * gives real riso its bright overprints (pink over blue reading as purple, not navy).
     */
    val overprint: Float = 0.55f,
    /** Halftone screening (0..1). `0` keeps continuous tone, `1` is a full dot screen. */
    val screen: Float = 0f,
    /** Halftone cell size in dp. */
    val dotSize: Float = 3f,
    /** Uneven ink laydown across the sheet (0..1). */
    val mottle: Float = 0.22f,
    /** Size of the mottling blotches, in dp. */
    val mottleSize: Float = 8f,
    /** Per-pixel ink speckle (0..1). */
    val grain: Float = 0.12f,
    /** Size of a speckle, in dp. */
    val grainSize: Float = 0.75f,
    /** Lateral paper-feed jitter, in dp. Wobbles the registration error along the sheet. */
    val wobble: Float = 0.4f,
    /** Ink gain — how far ink bleeds beyond where it was laid down (0..1). */
    val spread: Float = 0.15f,
    /** Seed for the mottling, speckle and wobble. Distinct from [RisoPaper.seed]. */
    val seed: Float = 3f,
)
