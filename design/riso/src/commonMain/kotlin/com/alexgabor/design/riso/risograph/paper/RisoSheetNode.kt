package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Links a pass to the sheet it prints onto. */
internal object RisoSheetKey

/**
 * One sheet: paints its stock, then lets its content draw on top.
 *
 * There is no layer and no effect over the content. The ink inside finds its sheet by walking up the
 * tree (see [RisoSheetKey]) and multiplies onto the stock already painted here, so what the sheet
 * does to the print it does through the ink, and nothing un-inked is touched.
 */
internal class RisoSheetNode(
    paper: RisoPaper,
    var cacheDir: String?,
) : Modifier.Node(), DrawModifierNode, TraversableNode, GlobalPositionAwareModifierNode {

    override val traverseKey: Any = RisoSheetKey

    var paper: RisoPaper = paper
        set(value) {
            if (field == value) return
            field = value
            redraw()
        }

    /**
     * Whatever prints onto this sheet and so has to be redrawn when the sheet changes under it: the
     * ink passes, which read its stock and its surface at draw time.
     */
    private val dependents = mutableSetOf<DrawModifierNode>()

    private var coordinates: LayoutCoordinates? = null
    private val brush = StockBrush()

    /** The tiles being waited for, and the wait itself. */
    private var awaiting: Pair<Tile, Tile>? = null
    private var awaitJob: Job? = null

    fun addDependent(node: DrawModifierNode) {
        dependents += node
    }

    fun removeDependent(node: DrawModifierNode) {
        dependents -= node
    }

    /**
     * The sheet's surface at this moment, requesting its tiles if nobody has yet. If they have not
     * landed, the sheet asks to be redrawn — and so does its ink — once they do.
     */
    fun surface(density: Float): SheetSurface {
        val paper = paper
        if (!paper.warps) return SheetSurface(paper, fine = null, coarse = null)
        val fine = PaperTiles.get(paper.fineTileKey(density), cacheDir)
        val coarse = PaperTiles.get(paper.coarseTileKey(), cacheDir)
        val surface = SheetSurface(paper, fine.shader, coarse.shader)
        if (!(fine.settled && coarse.settled)) redrawWhenLanded(fine, coarse)
        return surface
    }

    private fun redrawWhenLanded(fine: Tile, coarse: Tile) {
        if (!isAttached || awaiting == fine to coarse) return
        awaiting = fine to coarse
        awaitJob?.cancel()
        awaitJob = coroutineScope.launch {
            fine.await()
            coarse.await()
            redraw()
        }
    }

    private fun redraw() {
        if (!isAttached) return
        invalidateDraw()
        dependents.forEach { if (it.node.isAttached) it.invalidateDraw() }
    }

    /** Where the sheet's top-left sits on the page, or null while it is waiting to be placed. */
    val positionInRoot: Offset?
        get() = coordinates?.takeIf { it.isAttached }?.positionInRoot()

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onDetach() {
        coordinates = null
        awaiting = null
        awaitJob = null
    }

    override fun ContentDrawScope.draw() {
        val surface = surface(density)
        if (surface.paper.painted) {
            drawRect(ShaderBrush(brush.shader(surface, density)))
        }
        drawContent()
    }
}

internal data class RisoSheetElement(
    val paper: RisoPaper,
    val cacheDir: String?,
) : ModifierNodeElement<RisoSheetNode>() {

    override fun create() = RisoSheetNode(paper, cacheDir)

    override fun update(node: RisoSheetNode) {
        node.paper = paper
        node.cacheDir = cacheDir
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoPaper"
        properties["paper"] = paper
    }
}
