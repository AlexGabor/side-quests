package com.alexgabor.design.riso.risograph.region

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Leaves whatever this composable draws exactly as it is, where an ancestor would otherwise have
 * printed it.
 *
 * `risoPaper` is a render effect: its shader runs on the rasterized output of a whole layer, long
 * after the composables inside it are gone, so a child cannot opt out from the inside. Instead this
 * reports the composable's bounds up to every effect layer above it, and those shaders fetch the
 * region's pixels 1:1 and return them untouched — a window onto the layer, like a photograph tipped
 * onto a printed page.
 *
 * This only opts out of the *sheet* — the surface's warp and its shading. Ink is not applied here in
 * the first place: it is laid by [risoInk][com.alexgabor.design.riso.risograph.inks.risoInk], and a composable
 * that never names a drum was never going to be printed. Bypassing something inside a `risoInk` does
 * not take it off that drum.
 *
 * ### What passes through
 * The *region*, not the composable. Everything composited inside those bounds comes through
 * unprinted, including anything an ancestor draws over the top of it.
 *
 * The window is a rounded rectangle. A rotated child gets its axis-aligned bounding box, since that
 * is what its bounds report. Any number of them may sit inside one layer; each costs one distance
 * check per pixel, which is nothing at UI scale but is worth knowing before putting hundreds in.
 *
 * Outside any printed layer this does nothing.
 *
 * @param cornerRadius corner radius of the window, to match the content's own shape.
 */
fun Modifier.risoBypass(cornerRadius: Dp = 0.dp): Modifier = this then RisoBypassElement(cornerRadius)

/** One bypassed region, in the pixel coordinates of the effect layer that will honour it. */
data class RisoBypassRect(val rect: Rect, val cornerRadiusPx: Float)

/** Traversal key linking a [RisoBypassNode] to the effect layers above it. */
internal object RisoBypassKey

/** The bypassed regions inside one effect layer. */
internal typealias RisoBypassHost = RisoRegionHost<RisoBypassRect>

/**
 * Marks an effect layer as somewhere [risoBypass] can report to. Sits immediately before the
 * layer's `graphicsLayer` in the chain, so that its coordinate space *is* the layer's.
 */
internal fun Modifier.risoBypassHost(host: RisoBypassHost): Modifier =
    risoRegionHost(host, RisoBypassKey)

internal class RisoBypassNode(cornerRadius: Dp) : RisoRegionNode<RisoBypassRect>(RisoBypassKey) {

    var cornerRadius: Dp = cornerRadius
        set(value) {
            if (field == value) return
            field = value
            invalidateRegion()
        }

    val cornerRadiusPx: Float get() = with(requireDensity()) { cornerRadius.toPx() }

    override fun payload(bounds: Rect) = RisoBypassRect(bounds, cornerRadiusPx)
}

private data class RisoBypassElement(val cornerRadius: Dp) :
    ModifierNodeElement<RisoBypassNode>() {

    override fun create() = RisoBypassNode(cornerRadius)

    override fun update(node: RisoBypassNode) {
        node.cornerRadius = cornerRadius
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoBypass"
        properties["cornerRadius"] = cornerRadius
    }
}
