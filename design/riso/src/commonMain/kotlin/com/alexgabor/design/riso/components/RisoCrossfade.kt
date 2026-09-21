package com.alexgabor.design.riso.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.risograph.inks.risoFade

/**
 * Swaps one piece of content for another by running the press lighter over the old one while the
 * new one comes up — [RisoAnimatedVisibility]'s dissolve, with something arriving as something
 * leaves. See [risoFade], which is what does the work.
 *
 * By default both sides slow down as they pass through half ink, so the print in between — two
 * pieces of artwork, each at about half its dots — stays on the sheet long enough to be seen. See
 * [RisoFadeDefaults] for the curve.
 *
 * ### What it keeps hold of
 * While a swap is running both pieces of content are composed, stacked in the same place, the
 * newest on top; once it settles, only the current one is. Content shown on first composition is
 * printed at full ink straight away. Changing back to content that is still on its way out brings
 * that same content back, from whatever ink it had reached, rather than composing it twice.
 *
 * Content on its way out stops taking pointer input, as it does in [RisoAnimatedVisibility].
 *
 * ### Where it is the wrong thing
 * It does not animate size: the box is as big as the largest thing on it. Un-inked content inside
 * holds full strength through the swap unless it asks for the fade with
 * [com.alexgabor.design.riso.risograph.inks.risoFadeAsAlpha].
 *
 * @param targetState the content to show. A change starts a swap.
 * @param enterSpec how the incoming content prints in.
 * @param exitSpec how the outgoing content thins away.
 * @param contentKey what makes two states the same content. States with equal keys are one piece
 *   of content, and swapping between them does nothing.
 */
@Composable
fun <T> RisoCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    enterSpec: FiniteAnimationSpec<Float> = RisoFadeDefaults.crossfadeIn,
    exitSpec: FiniteAnimationSpec<Float> = RisoFadeDefaults.crossfadeOut,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label = "RisoCrossfade")
    // Everything on the page, oldest first so the newest prints on top.
    val onPage = remember(transition) { mutableStateListOf(transition.currentState) }
    val targetKey = contentKey(transition.targetState)

    if (transition.currentState == transition.targetState) {
        // Settled: whatever was on its way out has gone.
        if (onPage.size != 1 || onPage[0] != transition.targetState) {
            onPage.removeAll { contentKey(it) != targetKey }
        }
    }
    val slot = onPage.indexOfFirst { contentKey(it) == targetKey }
    when {
        slot == -1 -> onPage.add(transition.targetState)
        // The same content under a newer state: it keeps its place, and its ink.
        onPage[slot] != transition.targetState -> onPage[slot] = transition.targetState
    }

    Box(modifier) {
        onPage.forEach { state ->
            val stateKey = contentKey(state)
            key(stateKey) {
                val fade by transition.animateFloat(
                    transitionSpec = {
                        if (contentKey(this.targetState) == stateKey) enterSpec else exitSpec
                    },
                    label = "risoCrossfade",
                ) { if (contentKey(it) == stateKey) 1f else 0f }
                Box(Modifier.risoFade(fade).blockPointerInput(block = stateKey != targetKey)) {
                    content(state)
                }
            }
        }
    }
}
