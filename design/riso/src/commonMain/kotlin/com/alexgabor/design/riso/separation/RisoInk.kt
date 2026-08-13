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
 * The region names **which drums are on the press**; each pixel's own colour still decides how much
 * of each it takes, exactly as the separation always did — just over these drums instead of the
 * whole rack. So one region can hold artwork of several colours and each prints on the drum it was
 * drawn in: a border in the content ink, a filled pill in the accent, both inside the same
 * component. Tints, gradients, type and antialiased edges all keep working, and a fill authored with
 * [risoOverprint][com.alexgabor.design.riso.print.risoOverprint] from these same inks separates back
 * onto them at the coverages it was authored with.
 *
 * Name the ink **as it is loaded on the drum** — `RisoColors.inks.purple`, not
 * `purple.onRisoPaper()`. The second is what that ink looks like once printed, which is a different
 * colour; it will usually still resolve to the right drum, but only by being nearest to it. A colour
 * that is on no drum at all resolves to the nearest one the press is carrying.
 *
 * ### Scope
 * The *region*, not the composable — as with
 * [risoBypass][com.alexgabor.design.riso.bypass.risoBypass]. Everything composited inside those
 * bounds prints on these drums, including anything an ancestor draws over the top. A rotated child
 * gets its axis-aligned bounding box. Where regions overlap the smaller one wins, which for nested
 * composables is the innermost. Outside any printed layer this does nothing.
 *
 * A colour prints on at most three drums, so if more are named only the first three are loaded.
 *
 * A region's passes also only pick up the region's own artwork: ink thrown past these bounds by
 * [offsetScale] finds bare paper rather than whatever happens to be drawn next door.
 *
 * ### Printing nothing
 * Naming no inks at all is a **knockout**: the region prints on no drums and comes off the press as
 * bare stock. That is the opposite of `risoBypass`, which hands the content back untouched — a
 * knockout removes it. Nothing drifts into a knockout either, so it takes no fringe from
 * neighbouring passes.
 *
 * @param inks the drums to load, as the inks themselves rather than as they print. Empty is a
 *   knockout. Beyond three, only the first three are loaded.
 * @param offsetScale multiplies this region's registration error. `1` is the rack as loaded, `0`
 *   prints it in perfect register — worth having for small type, which is where misregistration
 *   costs legibility first — and larger values throw the passes apart on purpose. Thin artwork is
 *   sampled from wherever the pass lands, so a large value displaces a border or a glyph rather than
 *   fringing it. Has no effect on a knockout, where no drum runs.
 * @param cornerRadius corner radius of the region, to match the content's own shape.
 */
fun Modifier.risoInk(
    inks: List<Color> = emptyList(),
    offsetScale: Float = 1f,
    cornerRadius: Dp = 0.dp,
): Modifier = this then RisoInkElement(inks, offsetScale, cornerRadius)

/** [risoInk] on one drum. */
fun Modifier.risoInk(
    ink: Color,
    offsetScale: Float = 1f,
    cornerRadius: Dp = 0.dp,
): Modifier = risoInk(listOf(ink), offsetScale, cornerRadius)

/** [risoInk] on two drums. */
fun Modifier.risoInk(
    first: Color,
    second: Color,
    offsetScale: Float = 1f,
    cornerRadius: Dp = 0.dp,
): Modifier = risoInk(listOf(first, second), offsetScale, cornerRadius)

/** [risoInk] on three drums, which is as many as a colour prints on. */
fun Modifier.risoInk(
    first: Color,
    second: Color,
    third: Color,
    offsetScale: Float = 1f,
    cornerRadius: Dp = 0.dp,
): Modifier = risoInk(listOf(first, second, third), offsetScale, cornerRadius)

/**
 * One region's ink intent, in the pixel coordinates of the effect layer that will honour it.
 *
 * [inks] are carried as authored — colours, not slots. Which drum each is on depends on the rack the
 * press is running, which the region has no way of knowing; the print modifier resolves it when it
 * sets the uniforms.
 */
data class RisoInkRect(
    val rect: Rect,
    val cornerRadiusPx: Float,
    val inks: List<Color>,
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
    inks: List<Color>,
    offsetScale: Float,
    cornerRadius: Dp,
) : RisoRegionNode<RisoInkRect>(RisoInkKey) {

    var inks: List<Color> = inks
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
        inks = inks,
        offsetScale = offsetScale,
    )
}

private data class RisoInkElement(
    val inks: List<Color>,
    val offsetScale: Float,
    val cornerRadius: Dp,
) : ModifierNodeElement<RisoInkNode>() {

    override fun create() = RisoInkNode(inks, offsetScale, cornerRadius)

    override fun update(node: RisoInkNode) {
        node.inks = inks
        node.offsetScale = offsetScale
        node.cornerRadius = cornerRadius
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoInk"
        properties["inks"] = inks
        properties["offsetScale"] = offsetScale
        properties["cornerRadius"] = cornerRadius
    }
}
