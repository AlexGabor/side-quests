package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.graphics.RenderEffect

/**
 * The sheet's render effect, held against the bypass regions it was built from.
 *
 * An effect bakes in its shader's uniforms, which is why [risoPaper] builds one at draw time rather
 * than in composition: the bypass bounds move with every layout pass, and routing them through a
 * recomposition would land them a frame late. Moving is the exception, though. The
 * [host][com.alexgabor.design.riso.risograph.region.RisoRegionHost] publishes a new list only when
 * the regions really change, so the list an effect was built from is enough to say whether it still
 * describes the sheet — and a screen carrying no bypassed regions at all, which is most of them,
 * builds one effect and keeps it instead of one per frame under everything it draws.
 *
 * Holds only for as long as the shader's other uniforms do. Whoever remembers this is keyed on the
 * stock, the size and the surface, so a sheet that changed any of them starts with nothing held.
 */
internal class SheetEffect {

    private var regions: List<*>? = null
    private var effect: RenderEffect? = null

    /** The effect for [regions], built by [build] if the one held was for a different set. */
    fun forRegions(regions: List<*>, build: () -> RenderEffect): RenderEffect {
        val held = effect
        // Identity rather than equality: the host hands out a new list precisely when one moved.
        if (held != null && this.regions === regions) return held
        return build().also {
            this.regions = regions
            effect = it
        }
    }
}
