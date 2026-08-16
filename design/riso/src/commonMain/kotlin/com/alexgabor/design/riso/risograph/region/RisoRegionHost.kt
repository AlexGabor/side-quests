package com.alexgabor.design.riso.risograph.region

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
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.node.traverseAncestors

/**
 * The machinery a composable needs in order to say something about itself to the effect layer above
 * it.
 *
 * A render effect runs on the rasterized output of a whole layer, long after the composables inside
 * it are gone, so a child cannot address its shader from the inside. Instead it reports its bounds
 * up to every effect layer above it, and those shaders test the region per pixel. `risoBypass` uses
 * this to ask to be left alone; `risoInk` uses it to name the drums it prints on.
 *
 * What each carries differs, so the payload is [T] and the traversal key is per feature — two
 * features must not find each other's hosts.
 */

/**
 * Uniform-array capacity for [count] regions: the next power of two, at least 4. Bucketing keeps a
 * UI that adds and removes a region from recompiling its shaders each time.
 */
internal fun regionCapacity(count: Int): Int {
    var capacity = MIN_REGION_CAPACITY
    while (capacity < count) capacity *= 2
    return capacity
}

private const val MIN_REGION_CAPACITY = 4

/**
 * The regions of one kind inside one effect layer.
 *
 * [regions] is written on every layout pass and meant to be read at *draw* time, so that a region
 * moving with a scroll invalidates the layer without going through a recomposition — routing it
 * through composition would land the new bounds a frame late, and the region would visibly trail
 * its content on a fling.
 *
 * [peakRegionCount] is what composition reads, to size the shader's uniform arrays. It is the
 * high-water mark rather than the live count, because shrinking it would recompile the shader: a
 * list whose visible regions swing across a capacity boundary would otherwise recompile on every
 * crossing — measured at 86 compilations in a single scroll. Growing only costs a few zeroed
 * uniforms, since the shader's loop stops at the live count regardless.
 */
internal class RisoRegionHost<T> {
    var regions by mutableStateOf<List<T>>(emptyList())
        private set

    var peakRegionCount by mutableIntStateOf(0)
        private set

    private var coordinates: LayoutCoordinates? = null

    /** Insertion-ordered, so the regions keep a stable order across recomputes. */
    private val nodes = LinkedHashSet<RisoRegionNode<T>>()

    fun onPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        recompute()
    }

    fun add(node: RisoRegionNode<T>) {
        if (nodes.add(node)) recompute()
    }

    fun remove(node: RisoRegionNode<T>) {
        if (nodes.remove(node)) recompute()
    }

    fun recompute() {
        val layer = coordinates?.takeIf { it.isAttached }
        val next = if (layer == null) {
            emptyList()
        } else {
            nodes.mapNotNull { node ->
                val bounds = node.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
                node.payload(layer.localBoundingBoxOf(bounds))
            }
        }
        // Only publish real movement: an unchanged list would invalidate the layer every pass.
        if (next != regions) regions = next
        if (next.size > peakRegionCount) peakRegionCount = next.size
    }
}

/**
 * Marks an effect layer as somewhere regions of [traverseKey]'s kind can report to. Sits immediately
 * before the layer's `graphicsLayer` in the chain, so that its coordinate space *is* the layer's.
 */
internal fun <T> Modifier.risoRegionHost(host: RisoRegionHost<T>, traverseKey: Any): Modifier =
    this then RisoRegionHostElement(host, traverseKey)

internal class RisoRegionHostNode<T>(var host: RisoRegionHost<T>, override val traverseKey: Any) :
    Modifier.Node(), TraversableNode, GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        host.onPositioned(coordinates)
    }
}

private data class RisoRegionHostElement<T>(
    val host: RisoRegionHost<T>,
    val traverseKey: Any,
) : ModifierNodeElement<RisoRegionHostNode<T>>() {

    override fun create() = RisoRegionHostNode(host, traverseKey)

    override fun update(node: RisoRegionHostNode<T>) {
        node.host = host
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoRegionHost"
    }
}

/**
 * A composable reporting itself to every host of [traverseKey]'s kind above it. Subclasses supply
 * the payload — what this region has to say, given where it landed in the layer.
 */
internal abstract class RisoRegionNode<T>(private val traverseKey: Any) :
    Modifier.Node(), GlobalPositionAwareModifierNode {

    var coordinates: LayoutCoordinates? = null
        private set

    /** What to report for this region, given its bounds in the effect layer's coordinates. */
    abstract fun payload(bounds: Rect): T

    /** Every effect layer above this node, i.e. each layer of a nested print. */
    private val hosts = mutableListOf<RisoRegionHost<T>>()

    /** Call after changing anything [payload] reads, so the hosts pick the new value up. */
    fun invalidateRegion() {
        hosts.forEach { it.recompute() }
    }

    override fun onAttach() {
        traverseAncestors(traverseKey) { ancestor ->
            @Suppress("UNCHECKED_CAST")
            (ancestor as? RisoRegionHostNode<T>)?.host?.let {
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
