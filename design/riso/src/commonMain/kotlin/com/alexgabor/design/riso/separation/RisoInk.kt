package com.alexgabor.design.riso.separation

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.region.RisoRegionHost
import com.alexgabor.design.riso.region.RisoRegionNode
import com.alexgabor.design.riso.region.risoRegionHost

/**
 * Names the drums whatever this composable draws is printed with, instead of leaving the press to
 * work them out from the colour.
 *
 * `risoPrint` only ever sees the composited raster, so it recovers coverages by separating the pixel
 * against the rack — an inference, and an ambiguous one. A mix of two inks far apart on the colour
 * wheel is indistinguishable from a mix of the inks that sit between them, so the press reaches for
 * whichever of the two it can print in one wedge. The *colour* comes back right either way; the
 * drums may not, and since each drum carries its own registration error, screen angle and mottle,
 * that shows up as texture nobody asked for. This says which drums to use and settles it.
 *
 * ### What it fixes, and what it leaves alone
 * The region fixes **which drums, and in what ratio**. How much ink lands is still read per pixel
 * from the artwork, so tints, gradients, type and antialiased edges all keep working inside the
 * region — a flat fill is simply the case where that lands at full strength. Author the fill with
 * [risoOverprint][com.alexgabor.design.riso.print.risoOverprint] and the same coverages and the
 * colour round-trips exactly.
 *
 * ### Scope
 * The *region*, not the composable — as with
 * [risoBypass][com.alexgabor.design.riso.bypass.risoBypass]. Everything composited inside those
 * bounds prints on these drums, including anything an ancestor draws over the top. A rotated child
 * gets its axis-aligned bounding box. Where regions overlap the smaller one wins, which for nested
 * composables is the innermost. Outside any printed layer this does nothing.
 *
 * A press prints a colour on at most three drums, so if more are named only the three largest
 * coverages are loaded.
 *
 * ### Printing nothing
 * Naming no inks at all is a **knockout**: the region prints on no drums and comes off the press as
 * bare stock. That is the opposite of `risoBypass`, which hands the content back untouched — a
 * knockout removes it. Nothing drifts into a knockout either, so it takes no fringe from
 * neighbouring passes.
 *
 * @param recipe the drums to print with, each paired with its coverage as on a press's tint scale.
 * @param offsetScale multiplies this region's registration error. `1` is the rack as loaded, `0`
 *   prints it in perfect register — worth having for small type, which is where misregistration
 *   costs legibility first — and larger values throw the passes apart on purpose. Has no effect on
 *   a knockout, where no drum runs.
 * @param cornerRadius corner radius of the region, to match the content's own shape.
 */
fun Modifier.risoInk(
    vararg recipe: Pair<Color, Float>,
    offsetScale: Float = 1f,
    cornerRadius: Dp = 0.dp,
): Modifier = this then RisoInkElement(recipe.toList(), offsetScale, cornerRadius)

/**
 * One region's ink intent, in the pixel coordinates of the effect layer that will honour it.
 *
 * [recipe] is carried as authored — colours, not slots. Which drum each colour is on depends on the
 * rack the press is running, which the region has no way of knowing; the print modifier resolves it
 * when it sets the uniforms.
 */
data class RisoInkRect(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val recipe: List<Pair<Color, Float>>,
    val offsetScale: Float,
)

/** Traversal key linking a [RisoInkNode] to the effect layers above it. */
internal object RisoInkKey

/** The ink intent regions inside one effect layer. */
internal typealias RisoInkHost = RisoRegionHost<RisoInkRect>

/**
 * Marks an effect layer as somewhere [risoInk] can report to. Sits immediately before the layer's
 * `graphicsLayer` in the chain, so that its coordinate space *is* the layer's.
 */
internal fun Modifier.risoInkHost(host: RisoInkHost): Modifier = risoRegionHost(host, RisoInkKey)

internal class RisoInkNode(
    recipe: List<Pair<Color, Float>>,
    offsetScale: Float,
    cornerRadius: Dp,
) : RisoRegionNode<RisoInkRect>(RisoInkKey) {

    var recipe: List<Pair<Color, Float>> = recipe
        set(value) {
            if (field == value) return
            field = value
            invalidateRegion()
        }

    var offsetScale: Float = offsetScale
        set(value) {
            if (field == value) return
            field = value
            invalidateRegion()
        }

    var cornerRadius: Dp = cornerRadius
        set(value) {
            if (field == value) return
            field = value
            invalidateRegion()
        }

    override fun payload(bounds: Rect) = RisoInkRect(
        rect = bounds,
        cornerRadiusPx = with(requireDensity()) { cornerRadius.toPx() },
        recipe = recipe,
        offsetScale = offsetScale,
    )
}

private data class RisoInkElement(
    val recipe: List<Pair<Color, Float>>,
    val offsetScale: Float,
    val cornerRadius: Dp,
) : ModifierNodeElement<RisoInkNode>() {

    override fun create() = RisoInkNode(recipe, offsetScale, cornerRadius)

    override fun update(node: RisoInkNode) {
        node.recipe = recipe
        node.offsetScale = offsetScale
        node.cornerRadius = cornerRadius
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoInk"
        properties["recipe"] = recipe
        properties["offsetScale"] = offsetScale
        properties["cornerRadius"] = cornerRadius
    }
}
