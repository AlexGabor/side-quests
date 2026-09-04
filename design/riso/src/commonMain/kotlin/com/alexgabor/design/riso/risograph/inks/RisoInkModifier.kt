package com.alexgabor.design.riso.risograph.inks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
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
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled
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
 * to print on bare stock — see [risoKnockout], which takes that ink back off.
 *
 * ### Printing nothing
 * Naming no inks is a **knockout**, which is [risoKnockout] and is documented there.
 *
 * ### With the press stood down
 * Nothing at all: with `effectsEnabled = false` on the theme this adds no modifier, so the content
 * reaches the canvas as it was drawn — in its own colors, flat and in register — and no pass, layer
 * or shader is made for it. See [RisoTheme].
 *
 * @param inks the drums to load, as the inks themselves rather than as they print. Empty is a
 *   knockout. Beyond three, only the first three are loaded.
 * @param offsetScale multiplies this pass's registration error. `1` is the rack as loaded, `0` prints
 *   in perfect register — worth having for small type, where misregistration costs legibility first —
 *   and larger values throw the passes apart on purpose.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoInk(mix: RisoMix, offsetScale: Float = 1f): Modifier =
    risoInk(mix.drums, offsetScale)

@Composable
@ReadOnlyComposable
fun Modifier.risoInk(inks: List<Color>, offsetScale: Float = 1f): Modifier =
    if (!LocalRisoEffectsEnabled.current) this
    else this then RisoInkElement(inks, offsetScale, RisoTheme.press, RisoTheme.colors.paper)

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

/**
 * A hole in the pass enclosing this one — a frisket laid on the sheet rather than a plate run
 * through the press.
 *
 * Whatever this draws is taken back out of every drum of the pass above, at one place on the sheet
 * for all of them, so the region comes off as bare stock however far those drums throw. That last
 * part is the whole of it. Paper-colored artwork drawn into a two-drum pass is a hole in each drum
 * separately, and because each drum carries the artwork off register by its own error the two holes
 * land apart and each fills the other in — reversed-out type comes off the press doubled, one ghost
 * per ink. A frisket is cut once, and the ink around it moves under it.
 *
 * The hole reaches through the enclosing pass and stops there, which is the same rule nesting
 * already follows: a tint drawn by a pass further out still prints underneath. Knock that one out
 * too if the region is meant to reach the stock itself.
 *
 * A [risoInk] nested inside a knockout still prints, since the innermost still wins — it lands on
 * the bare stock the frisket left. It is also a hole in the frisket, so the enclosing pass keeps its
 * ink underneath it.
 *
 * Different from [risoBypass][com.alexgabor.design.riso.risograph.region.risoBypass], which hands
 * the content back untouched rather than taking ink away.
 *
 * With the press stood down there is no ink to take back off, and the frisket's own artwork — which
 * is what the hole would have shown through — is simply drawn where it stands.
 *
 * @param offsetScale how much of the enclosing pass's own throw the hole follows — a fraction of
 *   that pass's, not a scale of the drum's error the way [risoInk]'s is. `0` pins the hole to the
 *   sheet, which is what type wants. `1` sits it back inside the artwork, exactly where drawing the
 *   hole there would have put it.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoKnockout(offsetScale: Float = 0f): Modifier = risoInk(emptyList(), offsetScale)

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
            // Whether this is a hole is read by the pass that would have to punch it, and that pass
            // has this frame's answer baked into recordings of its own.
            above?.invalidateDraw()
        }

    var offsetScale: Float = offsetScale
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
            // Likewise: on a knockout this says how far the hole follows the drum, and the pass
            // above is the one that reads it.
            above?.invalidateDraw()
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

    /**
     * This node's hole in the pass above, one layer per drum that pass runs.
     *
     * One layer will not do. A layer's translation is read when the display list that draws it is
     * replayed, not when it is recorded, and every drum's pass is recorded before any of them is
     * replayed — so a single punch would hand all of them whichever drum's offset was written last.
     */
    private val punches = mutableListOf<GraphicsLayer>()

    /** Lays no ink of its own down: a hole, not a plate. See [risoKnockout]. */
    private val isKnockout: Boolean get() = inks.isEmpty()

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
        // The pass above holds this node's hole in recordings of its own. Releasing the punches
        // under it is safe — a released layer draws nothing — but it would leave the hole gone and
        // the ink back until something else happened to invalidate it.
        above?.invalidateDraw()
        above?.below?.remove(this)
        above = null
        coordinates = null
        val context = requireGraphicsContext()
        content?.let(context::releaseGraphicsLayer)
        content = null
        passes.forEach { context.releaseGraphicsLayer(it.layer) }
        passes.clear()
        punches.forEach(context::releaseGraphicsLayer)
        punches.clear()
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
        layDown(this, Offset.Zero, this@RisoPassNode)
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
     *
     * [host] owns the canvas all of this reaches, which is this node when it draws itself and stays
     * that same node all the way down the nesting: every pass below is laid into the same scope.
     */
    private fun layDown(scope: DrawScope, origin: Offset, host: RisoPassNode) {
        val content = content
        val artwork = visibleArtwork(host)
        if (content != null && !artwork.isEmpty) {
            val onPage = (coordinates?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero)
            resolveDrums().forEachIndexed { index, drum ->
                val pass = pass(index)
                // Anything but SrcOver forces the layer through an offscreen buffer, and that path
                // composites the layer by its own transform rather than by the canvas's — so the
                // registration error is carried on the layer, not by translating around the draw,
                // which the offscreen path would quietly drop. Clipping bounds that buffer to the
                // artwork as well: an unclipped one is sized to the whole destination, and the pass
                // shader would be run over the entire screen once per drum.
                //
                // The slip is kept apart from the origin because a frisket compensates the error and
                // not the placement: it comes back by exactly what the plate went out by, and by
                // nothing else.
                val slip = drum.registration * offsetScale * scope.density
                val shift = origin + slip
                with(scope) {
                    // Cut before the pass is opened. Recording a layer inside the recording that
                    // draws it works, but only by accident of the scope restoring itself.
                    cutKnockouts(index, slip)
                    pass.layer.record(contentSize) {
                        drawLayer(content)
                        punchKnockouts(index)
                    }
                    // Clipped to an outline named outright rather than to the layer's own bounds.
                    // Left implicit, the clip is resolved once against whatever the layer measured
                    // at the time and is never resolved again: on Skia that means a pass set up
                    // before its first recording clips to nothing and stays blank, and a pass whose
                    // artwork later grows — a window pulled wider — keeps printing at its old width
                    // with the rest cut off. Naming the rect makes it the artwork's, every frame —
                    // and the artwork's is the part of it the ancestors leave showing, which is what
                    // keeps this pass off the rest of the page. See [visibleArtwork].
                    pass.layer.setRectOutline(artwork.topLeft, artwork.size)
                    pass.layer.clip = true
                    pass.layer.translationX = shift.x
                    pass.layer.translationY = shift.y
                    pass.layer.blendMode = BlendMode.Multiply
                    // The punches take alpha out of this pass and out of nothing else. Multiply and
                    // the shader each force a buffer of their own already; naming it is what says
                    // the punches depend on there being one, rather than leaving that to whether
                    // this pass happens to have an effect to run.
                    pass.layer.compositingStrategy = CompositingStrategy.Offscreen
                    pass.layer.renderEffect = pass.shader.effect(drum.spec(density, onPage))
                    drawLayer(pass.layer)
                }
            }
        }
        // Whatever stood aside prints on top, at its own place on the page — every registered pass,
        // not only the ones that happened to re-record this frame, since a pass whose content is
        // unchanged still has to reach the sheet. A pass nested two deep recurses through here in
        // turn, each level resolving against the one that draws it.
        below.forEach { it.layDown(scope, origin + it.originIn(this), host) }
    }

    /**
     * Records and places this pass's holes for one drum, ready for [punchKnockouts] to draw.
     *
     * The pass layer carries [slip] as its own translation, so a punch put back by [slip] inside the
     * recording lands at the knockout's place on the page whatever the drum did — the same hole on
     * the sheet for every pass, which is the point of the thing. The knockout's own [offsetScale]
     * mixes between the two: `0` compensates the whole slip and pins the hole to the sheet, `1`
     * compensates none of it and the hole rides the drum, which is where drawing it into the artwork
     * would have put it.
     */
    private fun DrawScope.cutKnockouts(drumIndex: Int, slip: Offset) {
        below.forEach { child ->
            if (!child.isKnockout) return@forEach
            val artwork = child.content ?: return@forEach
            if (child.contentSize == IntSize.Zero) return@forEach
            // Not knowing where the hole goes is not the same as it going at the top left: a punch
            // in the wrong place takes ink off artwork that was meant to keep it. A knockout that
            // has not been placed yet waits for the frame that places it, which costs one frame of
            // un-reversed type at worst.
            val at = child.originOrNullIn(this@RisoPassNode) ?: return@forEach
            // A frisket is cut by whatever the hole's own ancestors leave showing, for the same
            // reason a plate is and by the same escape: this is the enclosing pass placing the
            // child's artwork, so a scroller between them would otherwise take ink off the sheet
            // somewhere the child was never on it. See [visibleArtwork].
            val hole = child.visibleArtwork(this@RisoPassNode)
            if (hole.isEmpty) return@forEach

            val punch = child.punch(drumIndex, requireGraphicsContext())
            punch.record(child.contentSize) { drawLayer(artwork) }
            punch.setRectOutline(hole.topLeft, hole.size)
            punch.clip = true
            punch.blendMode = BlendMode.DstOut
            // Same reason the pass's own shift rides the layer: a blend mode forces the offscreen
            // path, which composites by the layer's transform and drops the canvas's.
            punch.compositingStrategy = CompositingStrategy.Offscreen
            val back = at - slip * (1f - child.offsetScale)
            punch.translationX = back.x
            punch.translationY = back.y
        }
    }

    /** Draws what [cutKnockouts] prepared, inside the pass recording it cuts into. */
    private fun DrawScope.punchKnockouts(drumIndex: Int) {
        below.forEach { child ->
            if (!child.isKnockout) return@forEach
            if (child.content == null || child.contentSize == IntSize.Zero) return@forEach
            if (child.coordinates?.isAttached != true) return@forEach
            // Cut nothing this frame, so draw nothing: the layer still holds the last frame it was
            // cut on, and drawing that would put a stale hole back.
            if (child.visibleArtwork(this@RisoPassNode).isEmpty) return@forEach
            child.punches.getOrNull(drumIndex)?.let { drawLayer(it) }
        }
    }

    /**
     * How much of this node's artwork survives the clips [host]'s canvas does not already apply, in
     * the artwork's own coordinates.
     *
     * A nested pass never reaches the canvas by itself — the pass above lays it down, into *that*
     * pass's scope, at the offset between them. Every clip standing between the two is stepped over
     * on the way, and a scroller in that gap gets no say at all: a track label scrolled out of a
     * `LazyRow` still prints where the layout put it, which is off the row, off the card holding it,
     * and onto the next pane. Asking the layout what [host] can see of this node puts those clips
     * back, all of them, without any of them having to be named here.
     *
     * Only those. The clips above [host] are already on its canvas, and they are re-applied wherever
     * that canvas is replayed, where a rect named here is fixed at the draw that named it. The
     * difference is the whole reason this is asked of [host] rather than of the root: a pass drawing
     * on its own canvas is scrolled by moving its layer, without redrawing, so a clip named against
     * the root freezes at whatever was visible on the last draw. A card that last printed as a sliver
     * at the edge of a list then stays a sliver as it scrolls into full view.
     *
     * Named before the drum's throw rather than after it, because the layer carries that throw as
     * its own translation and the clip is cut inside the layer: the edge moves out with the ink by
     * exactly the registration error, so a pass still bleeds past the scroller the way a pass is
     * meant to. That is what the whole-page pass got for free when it recorded content a scroller
     * had already clipped and then threw the whole recording — cut first, thrown second — and it is
     * why there is no allowance added here.
     *
     * The full artwork while either node is waiting to be placed, which is what it printed before.
     */
    private fun visibleArtwork(host: RisoPassNode): Rect {
        val artwork = Rect(Offset.Zero, contentSize.toSize())
        if (host === this) return artwork
        val mine = coordinates?.takeIf { it.isAttached } ?: return artwork
        val theirs = host.coordinates?.takeIf { it.isAttached } ?: return artwork
        return theirs.localBoundingBoxOf(mine, clipBounds = true)
            .translate(-theirs.localPositionOf(mine, Offset.Zero))
            .intersect(artwork)
    }

    /** Where this node's content starts, in [ancestor]'s coordinates. */
    private fun originIn(ancestor: RisoPassNode): Offset = originOrNullIn(ancestor) ?: Offset.Zero

    /** [originIn], or null while either node is still waiting to be placed. */
    private fun originOrNullIn(ancestor: RisoPassNode): Offset? {
        val mine = coordinates?.takeIf { it.isAttached } ?: return null
        val theirs = ancestor.coordinates?.takeIf { it.isAttached } ?: return null
        return theirs.localPositionOf(mine, Offset.Zero)
    }

    private fun pass(index: Int): Pass {
        while (passes.size <= index) {
            passes += Pass(requireGraphicsContext().createGraphicsLayer(), InkPass())
        }
        return passes[index]
    }

    private fun punch(index: Int, context: GraphicsContext): GraphicsLayer {
        while (punches.size <= index) punches += context.createGraphicsLayer()
        return punches[index]
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
