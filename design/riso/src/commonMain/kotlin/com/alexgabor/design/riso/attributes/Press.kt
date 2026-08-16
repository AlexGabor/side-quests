package com.alexgabor.design.riso.attributes

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.risograph.inks.densityOf
import com.alexgabor.design.riso.risograph.inks.RisoInk
import com.alexgabor.design.riso.risograph.inks.risoInkForSlot

internal val LocalPress = staticCompositionLocalOf { RisoPress }

val RisoPress = Press()

/**
 * The press itself: which drums are loaded, and how they lay ink down.
 *
 * This is house style rather than a per-call argument — a print is characterised by its press as much
 * as by its palette — so it lives on the theme beside the colors and the typography. The sheet is
 * the other half, and that *is* per-call: see [risoPaper][com.alexgabor.design.riso.risograph.paper.risoPaper].
 *
 * Nothing here iterates the rack per pixel. A composable prints on the drums it names with
 * [risoInk][com.alexgabor.design.riso.risograph.inks.risoInk], and each named drum costs one pass; the
 * rest of the rack is only ever consulted to look a color up. So loading more drums is free, and
 * [inks] can grow past the twelve [Inks] stocks whenever there are more to stock.
 */
@Immutable
data class Press(
    /**
     * One entry per drum, in printing order. The slot a color sits in fixes how that drum
     * misregisters, what angle it screens at and how it mottles, so an ink keeps the same character
     * wherever it is used — see [risoInkForSlot].
     */
    val inks: List<RisoInk> = RisoColors.inks.all.mapIndexed { slot, ink ->
        risoInkForSlot(slot, ink.color)
    },
    /**
     * Halftone screening (0..1). `0` keeps continuous tone; `1` is a full dot screen.
     *
     * Worth leaving on. Screened dots at different angles land beside each other rather than on top
     * of each other, which is the whole reason a real riso overprint reads bright — pink over blue
     * comes out purple rather than navy — and it costs nothing to get that from the dot geometry
     * instead of from a knob that averages the inks.
     */
    val screen: Float = 1f,
    /** Halftone cell size in dp. */
    val dotSize: Float = 1.5f,
    /** Uneven ink laydown across the sheet (0..1). */
    val mottle: Float = 0.22f,
    /** Size of the mottling blotches, in dp. */
    val mottleSize: Float = 8f,
    /** Per-pixel ink speckle (0..1). */
    val grain: Float = 0.12f,
    /** Size of a speckle, in dp. */
    val grainSize: Float = 0.75f,
    /** Ink gain — how far ink bleeds beyond where it was laid down (0..1). */
    val spread: Float = 0.15f,
    /**
     * How close to the stock a color has to be to come off the press unprinted (0..1), as a
     * fraction darker than the paper. The default swallows about two 8-bit levels, so artwork
     * authored to the stock color — and the antialiased edges of anything drawn on it — reads as
     * bare paper rather than as a tint no press could hold.
     */
    val tolerance: Float = 0.01f,
    /**
     * Seed for the mottling and speckle. Distinct from
     * [RisoPaper.seed][com.alexgabor.design.riso.risograph.paper.RisoPaper.seed], which seeds the sheet.
     */
    val seed: Float = 3f,
) {
    /**
     * Which drum carries [color], as an index into [inks].
     *
     * Exact first — an author naming an ink means that ink, and the fluorescents in particular are
     * close enough to their neighbours in density that a fit would not reliably pick them out.
     * Failing that, the nearest drum by density, so a color that was never loaded still prints on
     * the closest thing the press has rather than falling off it.
     */
    fun slotOf(color: Color): Int {
        val exact = inks.indexOfFirst { it.color.value == color.value }
        if (exact >= 0) return exact

        val target = densityOf(color)
        return inks.indices.minByOrNull { slot ->
            val density = densityOf(inks[slot].color)
            var sum = 0f
            repeat(3) { channel ->
                val delta = density[channel] - target[channel]
                sum += delta * delta
            }
            sum
        } ?: 0
    }
}
