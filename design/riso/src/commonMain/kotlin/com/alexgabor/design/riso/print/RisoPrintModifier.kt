package com.alexgabor.design.riso.print

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.RisoColors

/**
 * Re-prints whatever the composable draws as a Risograph print of up to [MAX_INKS] spot colours.
 *
 * The content is separated into one ink coverage map per drum, each pass is sampled with its own
 * registration error (the pink/blue fringes of a real riso), and the passes are recombined
 * subtractively so overlaps produce a new colour instead of one ink hiding the other.
 *
 * Because the separation is done in ink-density space, content authored in (or near) the ink
 * colours round-trips exactly; arbitrary colours are projected onto the inks available, which is
 * lossy in the same way sending an RGB artwork to a two-drum press is.
 *
 * ### Authoring
 * The modifier only sees the composited result, so **overlaps have to be authored subtractively**
 * for there to be anything to overprint. Painting an opaque shape over another knocks the ink
 * underneath out of the artwork, exactly as a knockout does on a real press; draw the upper shape
 * with `BlendMode.Multiply` instead and the overlap arrives as a colour that separates into both
 * inks. For tints and specific overprints, [risoOverprint] gives you the exact colour to draw.
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

/** Parameters for [risoPrint]. */
data class RisoPrintParams(
    /** Stock colour the inks are printed onto. */
    val paper: Color = Color(0x00FFFFFF),
    /** One entry per drum, in printing order. Only the first [MAX_INKS] are used. */
    val inks: List<RisoInk> = listOf(
        RisoInk(RisoColors.inks.fluorescentPink, offsetX = -1.2f, offsetY = 0.8f, screenAngle = 15f),
        RisoInk(RisoColors.inks.blue, offsetX = 0.9f, offsetY = -0.5f, screenAngle = 75f),
    ),
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
    /** Seed for the mottling, speckle and wobble. */
    val seed: Float = 3f,
)
