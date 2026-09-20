package com.alexgabor.design.riso.risograph.inks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled
import kotlin.math.roundToInt

/**
 * Runs the press lighter over this composable: every pass inside it lays down [fade] of the ink it
 * would have laid, as though the artwork had been drawn that much fainter.
 *
 * Which is exactly what happens. A pass resolves coverage from the artwork's density *and its
 * alpha*, so thinning the artwork thins the ink — and because the dot screen sizes its dots by
 * coverage, what comes off the press is smaller dots of ink at full strength, with the stock showing
 * between them. That is a press being run light. An alpha over the *finished* print is a different
 * thing entirely: full-size dots going pale, which is a print seen through fog rather than less of
 * one, and it is what [androidx.compose.animation.fadeOut] and friends do to inked content.
 *
 * ### Scope
 * Everything printed inside, including components that print on drums of their own: the fade is read
 * by the passes themselves, not applied as a layer over them, so it reaches a pass the way nesting
 * reaches it rather than the way a `graphicsLayer` would. Nested fades multiply — `0.5` inside `0.5`
 * prints at `0.25` — and a fade says nothing about the artwork beside it.
 *
 * ### What does not thin
 * A knockout keeps cutting in full. The frisket is not ink: it is a piece of paper laid on the
 * sheet, and how much ink the press is laying has no bearing on what it holds off. Reversed-out
 * artwork therefore stays crisp against bare stock while the ink around it thins away.
 *
 * Neither does anything the press does not print — an image, a plain fill, anything outside a
 * [risoInk]. There is no ink there to lay less of. Such content takes the fade as ordinary
 * transparency instead, and only when it asks: see [risoFadeAsAlpha].
 *
 * ### With the press stood down
 * There is no coverage to thin, so the fade is a plain alpha over the content — the honest stand-in,
 * and the same one [risoInk] takes when it draws artwork in its own colors.
 *
 * @param fade how much of the ink to lay down, `0` for none and `1` for the print as it stands.
 *   Values outside that range are clamped.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoFade(fade: Float): Modifier {
    val clamped = fade.coerceIn(0f, 1f)
    return if (!LocalRisoEffectsEnabled.current) graphicsLayer { alpha = clamped }
    else this then RisoFadeElement(clamped)
}

/**
 * Takes the enclosing [risoFade] as plain transparency, for content the press does not print.
 *
 * A fade thins ink, and an image has no ink to thin — left alone it would hold full strength through
 * a fade and then vanish when whatever is hiding it takes it out of the layout. This is how it
 * leaves with everything else: the same fade, resolved from the same enclosing [risoFade], applied
 * as the alpha it can actually use.
 *
 * **Not for inked content.** A pass is already thinning itself by this fade; asking for it again
 * here would fade that pass twice, once as coverage and once as transparency — the fog this whole
 * arrangement exists to avoid. Nothing here can tell whether what it draws is printed, so this one
 * is on the caller.
 *
 * Outside any fade, and with the press stood down — where a fade is already a plain alpha over
 * everything inside it, this one included — it does nothing at all.
 */
@Composable
@ReadOnlyComposable
fun Modifier.risoFadeAsAlpha(): Modifier =
    if (!LocalRisoEffectsEnabled.current) this else this then RisoFadeAlphaElement

/** Links a fade to the fades and the passes below it. */
internal object RisoFadeKey

/**
 * How light the press is running over a subtree.
 *
 * Holds no drawing of its own: it is read, at draw time, by the passes that print inside it. What it
 * owns is the invalidation — when the fade moves, everything that resolved against it has to be
 * given the chance to draw again, and only this node knows what those are.
 */
internal class RisoFadeNode(fade: Float) : Modifier.Node(), TraversableNode {

    override val traverseKey: Any = RisoFadeKey

    var fade: Float = fade
        set(value) {
            if (field == value) return
            field = value
            redraw()
        }

    /** The nearest fade above this one, which this one prints inside. */
    private var above: RisoFadeNode? = null

    /** Fades nested inside this one, which resolve through it and so move when it does. */
    private val below = mutableSetOf<RisoFadeNode>()

    /** What reads this fade when it draws: the passes inside it, and [risoFadeAsAlpha]. */
    private val dependents = mutableSetOf<DrawModifierNode>()

    /**
     * This fade and every fade it prints inside, multiplied.
     *
     * Two fades over the same artwork are two passes of a lighter press, not the heavier of the
     * two — a subtree at half ink inside a screen at half ink reaches the sheet at a quarter.
     */
    val resolved: Float get() = fade * (above?.resolved ?: 1f)

    /** How many nodes currently resolve against this fade. For tests. */
    val dependentCount: Int get() = dependents.size

    fun addDependent(node: DrawModifierNode) {
        dependents += node
    }

    fun removeDependent(node: DrawModifierNode) {
        dependents -= node
    }

    override fun onAttach() {
        traverseAncestors(RisoFadeKey) { ancestor ->
            above = (ancestor as RisoFadeNode).also { it.below.add(this) }
            false
        }
    }

    override fun onDetach() {
        above?.below?.remove(this)
        above = null
        below.clear()
        dependents.clear()
    }

    /**
     * Asks everything resolving against this fade to draw again, through the nested fades as well:
     * a pass registers with the nearest fade above it, which may not be this one, and a fade moving
     * two levels up still changes what that pass lays down.
     */
    private fun redraw() {
        dependents.forEach { if (it.node.isAttached) it.invalidateDraw() }
        below.forEach { it.redraw() }
    }
}

internal data class RisoFadeElement(val fade: Float) : ModifierNodeElement<RisoFadeNode>() {

    override fun create() = RisoFadeNode(fade)

    override fun update(node: RisoFadeNode) {
        node.fade = fade
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "risoFade"
        properties["fade"] = fade
    }
}

/** Links [risoFadeAsAlpha] to nothing; it only ever looks upwards. See [RisoFadeAlphaNode]. */
private object RisoFadeAlphaKey

/**
 * Draws un-inked content at the enclosing fade.
 *
 * Through a layer rather than by handing the alpha to each draw: content that overlaps itself would
 * otherwise be faded once per draw and come out darker where it laps, which is the same trap that
 * keeps a pass from carrying its fade on its own content layer.
 */
internal class RisoFadeAlphaNode : Modifier.Node(), DrawModifierNode, TraversableNode {

    override val traverseKey: Any = RisoFadeAlphaKey

    private var fadeNode: RisoFadeNode? = null
    private var layer: GraphicsLayer? = null

    override fun onAttach() {
        traverseAncestors(RisoFadeKey) { ancestor ->
            fadeNode = (ancestor as RisoFadeNode).also { it.addDependent(this) }
            false
        }
    }

    override fun onDetach() {
        fadeNode?.removeDependent(this)
        fadeNode = null
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val fade = fadeNode?.resolved ?: 1f
        // Nothing to say at full strength, and nothing to allocate for it either.
        if (fade >= 1f) {
            drawContent()
            return
        }
        if (fade <= 0f) return
        val layer = layer ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
        layer.alpha = fade
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
            this@draw.drawContent()
        }
        drawLayer(layer)
    }
}

internal object RisoFadeAlphaElement : ModifierNodeElement<RisoFadeAlphaNode>() {

    override fun create() = RisoFadeAlphaNode()

    override fun update(node: RisoFadeAlphaNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "risoFadeAsAlpha"
    }

    override fun equals(other: Any?) = other === this

    override fun hashCode() = RisoFadeAlphaKey.hashCode()
}
