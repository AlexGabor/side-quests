package com.alexgabor.design.riso.risograph.inks

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.RisoColors
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a fade is to the tree, rather than what it does to the ink — that is
 * `SheetRenderTest`, where it can be looked at.
 */
@OptIn(ExperimentalTestApi::class)
class RisoFadeTest {

    @Test
    fun aPassLeavesTheFadeWhenItIsTakenOutOfTheTree() = runComposeUiTest {
        var fade: RisoFadeNode? = null
        var printing by mutableStateOf(true)
        setContent {
            RisoTheme {
                Box(Modifier.risoFade(0.5f).then(ProbeElement { fade = it })) {
                    if (printing) Box(Modifier.risoInk(RisoColors.content))
                }
            }
        }
        waitForIdle()
        assertEquals(1, fade?.dependentCount, "the pass did not register with the fade")

        printing = false
        waitForIdle()
        // Left registered, the fade would keep a detached node alive and go on invalidating it
        // every frame it moved.
        assertEquals(0, fade?.dependentCount, "the pass was left registered after it detached")
    }

    @Test
    fun aFadeResolvesThroughTheFadesAboveIt() = runComposeUiTest {
        var outer: RisoFadeNode? = null
        var inner: RisoFadeNode? = null
        setContent {
            RisoTheme {
                Box(Modifier.risoFade(0.5f).then(ProbeElement { outer = it })) {
                    Box(Modifier.risoFade(0.5f).then(ProbeElement { inner = it }))
                }
            }
        }
        waitForIdle()
        assertEquals(0.5f, outer?.resolved)
        assertEquals(0.25f, inner?.resolved)
    }

    @Test
    fun aFadeIsClampedToWhatAPressCanRun() = runComposeUiTest {
        var over: RisoFadeNode? = null
        var under: RisoFadeNode? = null
        setContent {
            RisoTheme {
                Box(Modifier.risoFade(4f).then(ProbeElement { over = it }))
                Box(Modifier.risoFade(-1f).then(ProbeElement { under = it }))
            }
        }
        waitForIdle()
        assertEquals(1f, over?.resolved)
        assertEquals(0f, under?.resolved)
    }
}

/** Hands back the fade this sits inside, which is otherwise only ever read at draw time. */
private class ProbeNode(var onFound: (RisoFadeNode) -> Unit) : Modifier.Node(), TraversableNode {

    override val traverseKey: Any = ProbeNode::class

    override fun onAttach() {
        traverseAncestors(RisoFadeKey) { ancestor ->
            onFound(ancestor as RisoFadeNode)
            false
        }
    }
}

private data class ProbeElement(
    val onFound: (RisoFadeNode) -> Unit,
) : ModifierNodeElement<ProbeNode>() {

    override fun create() = ProbeNode(onFound)

    override fun update(node: ProbeNode) {
        node.onFound = onFound
    }
}
