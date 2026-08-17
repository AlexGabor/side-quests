package com.alexgabor.design.riso.risograph.inks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Press
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Prints whatever this composable draws on the drums named, one pass per drum.
 *
 * Each pass is the same artwork, laid down in one ink and thrown off register by that drum's own
 * error — the pink/blue fringes of a real riso. The passes multiply, so where two of them land on
 * the same pixel the inks stack and the overlap goes dark, exactly as ink on paper does.
 *
 * ### How a color becomes coverage
 * Naming a drum says *which* ink prints; the artwork's own color still says *how much*. Coverage is
 * the pixel's optical density resolved against the inks named, so one region can hold artwork of
 * several colors and each prints on the drum it was drawn in — a border in one ink, a fill in
 * another, inside the same component. Tints, gradients, type and antialiased edges all keep working,
 * and a fill authored with [risoOverprint][risoOverprint] from these
 * same inks separates back onto them at the coverages it was authored with.
 *
 * Name the ink **as it is loaded on the drum** — `RisoColors.inks.purple`, not
 * `purple.onRisoPaper()`. The second is what that ink looks like once printed, which is a different
 * color; it will usually still resolve to the right drum, but only by being nearest to it. A color
 * on no drum at all resolves to the nearest one the press is carrying.
 *
 * ### Scope
 * The composable and everything inside it, as one pass — not a rectangle over the layer. The
 * registration error moves the whole pass, so a shape carries its own fringe wherever it is drawn
 * and can never pick up a neighbour's artwork on the way.
 *
 * A color prints on at most three drums, so if more are named only the first three are loaded.
 * Beyond three the separation stops being exact, and a press that needed four drums for one color
 * would be a press with the wrong inks on it.
 *
 * ### Nesting
 * The innermost wins. A `risoInk` inside another prints on its own drums, and the outer one leaves a
 * hole where that artwork was rather than inking it a second time — so a component keeps its own
 * colors wherever it is dropped.
 *
 * The hole is in the *artwork*, not in the sheet: whatever the outer one draws behind the inner —
 * a panel's tint, a card's fill — still prints there, and the inner pass lands on top of it. That is
 * an overprint, and it is what a press would do. Knock the outer one out first if the inner is meant
 * to print on bare stock.
 *
 * ### Printing nothing
 * Naming no inks is a **knockout**: nothing is laid down and the region comes off the press as bare
 * stock. That is different from [risoBypass][com.alexgabor.design.riso.risograph.region.risoBypass],
 * which hands the content back untouched. A `risoInk` nested inside a knockout
 * still prints, since the innermost still wins.
 *
 * @param inks the drums to load, as the inks themselves rather than as they print. Empty is a
 *   knockout. Beyond three, only the first three are loaded.
 * @param offsetScale multiplies this pass's registration error. `1` is the rack as loaded, `0` prints
 *   in perfect register — worth having for small type, where misregistration costs legibility first —
 *   and larger values throw the passes apart on purpose. Has no effect on a knockout.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoInk(inks: List<Color>, offsetScale: Float = 1f): Modifier =
    this then RisoInkElement(inks, offsetScale, RisoTheme.press, RisoTheme.colors.paper)

/** [risoInk] on one drum. */
@Composable
@ReadOnlyComposable
fun Modifier.risoInk(ink: Color, offsetScale: Float = 1f): Modifier =
    risoInk(listOf(ink), offsetScale)

/** [risoInk] on two drums. */
@Composable
@ReadOnlyComposable
fun Modifier.risoInk(first: Color, second: Color, offsetScale: Float = 1f): Modifier =
    risoInk(listOf(first, second), offsetScale)

/** [risoInk] on three drums, which is as many as a color prints on. */
@Composable
@ReadOnlyComposable
fun Modifier.risoInk(first: Color, second: Color, third: Color, offsetScale: Float = 1f): Modifier =
    risoInk(listOf(first, second, third), offsetScale)

/** Links a pass to the passes above it, so that the innermost can take precedence. */
private object RisoPassKey

/** As many drums as a color separates onto exactly. See [separationRows]. */
private const val MAX_DRUMS = 3

/**
 * One composable's run through the press.
 *
 * The content is recorded once and replayed once per drum, each replay carrying that drum's ink
 * shader and translated by its registration error. Recording is what makes the passes honest: a
 * translated *layer* moves as a whole, where the shader this replaces re-read the composited page at
 * an offset and so picked up whatever artwork happened to be sitting there — the reason it needed
 * region bounds, a reach and a containment ranking to keep passes off their neighbours.
 */
internal class RisoPassNode(
    inks: List<Color>,
    offsetScale: Float,
    press: Press,
    paper: Color,
) : Modifier.Node(), DrawModifierNode, TraversableNode, GlobalPositionAwareModifierNode {

    override val traverseKey: Any = RisoPassKey

    var inks: List<Color> = inks
        set(value) {
            if (field == value) return
            field = value
            drums = null
            invalidateDraw()
        }

    var offsetScale: Float = offsetScale
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
        }

    var press: Press = press
        set(value) {
            if (field == value) return
            field = value
            drums = null
            invalidateDraw()
        }

    var paper: Color = paper
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
        }

    /** The drums [inks] resolved to, and their coverage rows. Rebuilt when either input changes. */
    private var drums: List<Drum>? = null

    private var content: GraphicsLayer? = null
    private var contentSize = IntSize.Zero
    private val passes = mutableListOf<Pass>()

    /** The nearest pass above this one, which this one takes precedence over. */
    private var above: RisoPassNode? = null

    /** Passes nested inside this one, which stand aside for it and are laid down by it. */
    private val below = mutableListOf<RisoPassNode>()

    /**
     * True only while this node's content is being recorded. Not what decides nesting — [above] does
     * — only whether a nested pass drawing right now is already inside this recording, and so needs
     * no frame asking for. See [draw].
     */
    private var recording = false

    private var coordinates: LayoutCoordinates? = null

    override fun onAttach() {
        // The first ancestor found is the nearest, which is the only one that matters: a pass hides
        // itself from the pass immediately above it, and that one in turn from the one above it.
        //
        // Registered here rather than at draw time. Which pass owns which is a fact about the tree,
        // and the tree is what says it; drawing does not, because a display list that contains a
        // child's display list lets the child be re-recorded on its own. Read as "is my ancestor
        // recording right now?", nesting held on the first frame and quietly came apart on every
        // frame the ancestor did not happen to redraw.
        traverseAncestors(RisoPassKey) { ancestor ->
            above = (ancestor as RisoPassNode).also { it.below.add(this) }
            false
        }
    }

    override fun onDetach() {
        above?.below?.remove(this)
        above = null
        coordinates = null
        val context = requireGraphicsContext()
        content?.let(context::releaseGraphicsLayer)
        content = null
        passes.forEach { context.releaseGraphicsLayer(it.layer) }
        passes.clear()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        val above = above
        if (above != null) {
            // These pixels are not the pass above's to ink, so nothing goes on the canvas here —
            // the recording above is left with a hole where this pass belongs, and the pass above
            // lays this one down itself once its own drums are on the sheet.
            //
            // Unless it is not drawing. A child can be re-recorded without its ancestor being
            // re-run, and then nothing would reach the screen at all, so the ancestor is asked for
            // a frame. It cannot loop: on that frame this node draws inside the ancestor's
            // recording, which is the one case that asks for nothing.
            //
            // Recording waits for that frame rather than happening here as well. A recording taken
            // outside the ancestor's is only overwritten by the one taken inside it a moment later,
            // and never reaches the sheet in between — on a list being scrolled that is every
            // pass's whole subtree recorded twice a frame.
            if (!above.recording) {
                above.invalidateDraw()
                return
            }
            recordContent()
            return
        }
        recordContent()
        layDown(this, Offset.Zero)
    }

    private fun ContentDrawScope.recordContent() {
        val layer = content
            ?: requireGraphicsContext().createGraphicsLayer().also { content = it }
        contentSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        recording = true
        try {
            // The DrawScope overload, not GraphicsLayer.record(density, layoutDirection, size):
            // only this one retargets the scope's canvas into the layer, and drawContent() draws to
            // the scope's canvas. The other records an empty layer and puts the content on screen.
            layer.record(contentSize) { this@recordContent.drawContent() }
        } finally {
            recording = false
        }
    }

    /**
     * Runs this pass's drums into [scope], and then every pass nested inside it.
     *
     * [origin] is where this node sits in the coordinate space of whoever is drawing it — zero when
     * it draws itself, and its offset within the pass above when that pass is laying it down.
     */
    private fun layDown(scope: DrawScope, origin: Offset) {
        val content = content
        if (content != null && contentSize != IntSize.Zero) {
            val onPage = (coordinates?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero)
            resolveDrums().forEachIndexed { index, drum ->
                val pass = pass(index)
                // Anything but SrcOver forces the layer through an offscreen buffer, and that path
                // composites the layer by its own transform rather than by the canvas's — so the
                // registration error is carried on the layer, not by translating around the draw,
                // which the offscreen path would quietly drop. Clipping bounds that buffer to the
                // artwork as well: an unclipped one is sized to the whole destination, and the pass
                // shader would be run over the entire screen once per drum.
                val shift = origin + drum.registration * offsetScale * scope.density
                with(scope) {
                    pass.layer.record(contentSize) { drawLayer(content) }
                    // Clipped to an outline named outright rather than to the layer's own bounds.
                    // Left implicit, the clip is resolved once against whatever the layer measured
                    // at the time and is never resolved again: on Skia that means a pass set up
                    // before its first recording clips to nothing and stays blank, and a pass whose
                    // artwork later grows — a window pulled wider — keeps printing at its old width
                    // with the rest cut off. Naming the rect makes it the artwork's, every frame.
                    pass.layer.setRectOutline(Offset.Zero, contentSize.toSize())
                    pass.layer.clip = true
                    pass.layer.translationX = shift.x
                    pass.layer.translationY = shift.y
                    pass.layer.blendMode = BlendMode.Multiply
                    pass.layer.renderEffect = pass.shader.effect(drum.spec(density, onPage))
                    drawLayer(pass.layer)
                }
            }
        }
        // Whatever stood aside prints on top, at its own place on the page — every registered pass,
        // not only the ones that happened to re-record this frame, since a pass whose content is
        // unchanged still has to reach the sheet. A pass nested two deep recurses through here in
        // turn, each level resolving against the one that draws it.
        below.forEach { it.layDown(scope, origin + it.originIn(this)) }
    }

    /** Where this node's content starts, in [ancestor]'s coordinates. */
    private fun originIn(ancestor: RisoPassNode): Offset {
        val mine = coordinates?.takeIf { it.isAttached } ?: return Offset.Zero
        val theirs = ancestor.coordinates?.takeIf { it.isAttached } ?: return Offset.Zero
        return theirs.localPositionOf(mine, Offset.Zero)
    }

    private fun pass(index: Int): Pass {
        while (passes.size <= index) {
            passes += Pass(requireGraphicsContext().createGraphicsLayer(), InkPass())
        }
        return passes[index]
    }

    private fun resolveDrums(): List<Drum> = drums ?: buildDrums().also { drums = it }

    private fun buildDrums(): List<Drum> {
        // Two names landing on the same drum would split its coverage between two rows and print it
        // twice, so they are folded together first.
        val slots = inks.take(MAX_DRUMS).map(press::slotOf).distinct()
        if (slots.isEmpty()) return emptyList()

        val rows = separationRows(slots.map { press.inks[it].color })
        return slots.mapIndexed { index, slot ->
            val ink = press.inks[slot]
            Drum(
                ink = ink.color,
                row = rows[index],
                registration = Offset(ink.offsetX, ink.offsetY),
                screenAngle = (ink.screenAngle * PI / 180.0).toFloat(),
                // Each drum mottles and speckles off its own phase, so no two passes blotch in the
                // same places and an overprint does not read as one thick ink.
                phase = press.seed + slot * 13.7f,
            )
        }
    }

    private fun Drum.spec(density: Float, onPage: Offset) = InkPassSpec(
        ink = ink,
        row = row,
        paper = paper,
        tolerance = press.tolerance,
        origin = onPage,
        screenAngle = screenAngle,
        phase = phase,
        screen = press.screen,
        dotSize = press.dotSize * density,
        mottle = press.mottle,
        mottleSize = press.mottleSize * density,
        grain = press.grain,
        grainSize = press.grainSize * density,
        spread = press.spread,
    )
}

/** One loaded drum, with everything the pass needs that does not change between draws. */
private class Drum(
    val ink: Color,
    val row: FloatArray,
    /** This drum's registration error, in dp, before [RisoPassNode.offsetScale]. */
    val registration: Offset,
    val screenAngle: Float,
    val phase: Float,
)

/** One drum's layer and the shader that inks it, held across draws so the shader compiles once. */
private class Pass(val layer: GraphicsLayer, val shader: InkPass)

private data class RisoInkElement(
    val inks: List<Color>,
    val offsetScale: Float,
    val press: Press,
    val paper: Color,
) : ModifierNodeElement<RisoPassNode>() {

    override fun create() = RisoPassNode(inks, offsetScale, press, paper)

    override fun update(node: RisoPassNode) {
        node.inks = inks
        node.offsetScale = offsetScale
        node.press = press
        node.paper = paper
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoInk"
        properties["inks"] = inks
        properties["offsetScale"] = offsetScale
    }
}
