package com.alexgabor.design.riso.bypass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Leaves whatever this composable draws exactly as it is, where an ancestor would otherwise have
 * printed it.
 *
 * `risoPrint` and `paperTexture` are render effects: their shaders run on the rasterized output of
 * a whole layer, long after the composables inside it are gone, so a child cannot opt out from the
 * inside. Instead this reports the composable's bounds up to every effect layer above it, and those
 * shaders fetch the region's pixels 1:1 and return them untouched — a window onto the layer, like a
 * photograph tipped onto a printed page.
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

/**
 * The bypassed regions inside one effect layer.
 *
 * [regions] is written on every layout pass and meant to be read at *draw* time, so that a region
 * moving with a scroll invalidates the layer without going through a recomposition — routing it
 * through composition would land the new bounds a frame late, and the window would visibly trail
 * its content on a fling.
 *
 * [peakRegionCount] is what composition reads, to size the shader's uniform arrays. It is the
 * high-water mark rather than the live count, because shrinking it would recompile the shader: a
 * list whose visible regions swing across a capacity boundary would otherwise recompile on every
 * crossing — measured at 86 compilations in a single scroll. Growing only costs a few zeroed
 * uniforms, since the shader's loop stops at the live count regardless.
 */
internal class RisoBypassHost {
    var regions by mutableStateOf<List<RisoBypassRect>>(emptyList())
        private set

    var peakRegionCount by mutableIntStateOf(0)
        private set

    private var coordinates: LayoutCoordinates? = null

    /** Insertion-ordered, so the regions keep a stable order across recomputes. */
    private val nodes = LinkedHashSet<RisoBypassNode>()

    fun onPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        recompute()
    }

    fun add(node: RisoBypassNode) {
        if (nodes.add(node)) recompute()
    }

    fun remove(node: RisoBypassNode) {
        if (nodes.remove(node)) recompute()
    }

    fun recompute() {
        val layer = coordinates?.takeIf { it.isAttached }
        val next = if (layer == null) {
            emptyList()
        } else {
            nodes.mapNotNull { node ->
                val bounds = node.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
                RisoBypassRect(layer.localBoundingBoxOf(bounds), node.cornerRadiusPx)
            }
        }
        // Only publish real movement: an unchanged list would invalidate the layer every pass.
        if (next != regions) regions = next
        if (next.size > peakRegionCount) peakRegionCount = next.size
    }
}

/**
 * Marks an effect layer as somewhere [risoBypass] can report to. Sits immediately before the
 * layer's `graphicsLayer` in the chain, so that its coordinate space *is* the layer's.
 */
internal fun Modifier.risoBypassHost(host: RisoBypassHost): Modifier =
    this then RisoBypassHostElement(host)

internal class RisoBypassHostNode(var host: RisoBypassHost) :
    Modifier.Node(), TraversableNode, GlobalPositionAwareModifierNode {

    override val traverseKey get() = RisoBypassKey

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        host.onPositioned(coordinates)
    }
}

private data class RisoBypassHostElement(val host: RisoBypassHost) :
    ModifierNodeElement<RisoBypassHostNode>() {

    override fun create() = RisoBypassHostNode(host)

    override fun update(node: RisoBypassHostNode) {
        node.host = host
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoBypassHost"
    }
}

internal class RisoBypassNode(cornerRadius: Dp) :
    Modifier.Node(), GlobalPositionAwareModifierNode {

    var cornerRadius: Dp = cornerRadius
        set(value) {
            if (field == value) return
            field = value
            hosts.forEach { it.recompute() }
        }

    var coordinates: LayoutCoordinates? = null
        private set

    val cornerRadiusPx: Float get() = with(requireDensity()) { cornerRadius.toPx() }

    /**
     * Every effect layer above this node — both `risoPrint` and `paperTexture` when they are
     * stacked, and each layer of a nested print.
     */
    private val hosts = mutableListOf<RisoBypassHost>()

    override fun onAttach() {
        traverseAncestors(RisoBypassKey) { ancestor ->
            (ancestor as? RisoBypassHostNode)?.host?.let {
                hosts += it
                it.add(this)
            }
            true
        }
    }

    override fun onDetach() {
        coordinates = null
        hosts.forEach { it.remove(this) }
        hosts.clear()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        hosts.forEach { it.recompute() }
    }
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
