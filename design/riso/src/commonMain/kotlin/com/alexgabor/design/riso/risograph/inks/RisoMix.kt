package com.alexgabor.design.riso.risograph.inks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled

@Immutable
data class RisoMix(
    /** The drums this is mixed on, each with its coverage on the press's tint scale. */
    val inks: List<Pair<Color, Float>>,
    /**
     * What this mix looks like unprinted.
     */
    val unprinted: Color,
) {
    val drums: List<Color> = inks.map { it.first }
}

/** [RisoMix] from inks named inline. */
fun RisoMix(vararg inks: Pair<Color, Float>, unprinted: Color): RisoMix =
    RisoMix(inks.asList(), unprinted)

/**
 * What this mix comes off the press as — or [RisoMix.unprinted], if the press is stood down.
 *
 * @param paper the stock it is printed on, which is the theme's unless this is going somewhere else.
 */
@Composable
@ReadOnlyComposable
fun RisoMix.color(paper: Color = RisoTheme.colors.paper): Color =
    if (LocalRisoEffectsEnabled.current) risoOverprint(paper, inks) else unprinted
