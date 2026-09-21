package com.alexgabor.design.riso.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.risograph.inks.risoFade

/**
 * Shows and hides [content] by running the press over it lighter and lighter, until there is no ink
 * left to lay — see [risoFade], which is what does the work.
 *
 * The plain [androidx.compose.animation.AnimatedVisibility] fades the *print* instead: it puts an
 * alpha on a layer over the finished artwork, so full-size halftone dots go pale where this thins
 * them away to nothing. Both are fades; only one of them is a press.
 *
 * ### What it keeps hold of
 * While either direction is running the content is composed and holds its place in the layout, so
 * nothing jumps part-way through; once hidden it is neither, and takes up no room. The animation
 * ends exactly on its target, so the frame it settles is the frame the space is released.
 *
 * Content on its way out stops taking pointer input, which is a thing
 * [androidx.compose.animation.AnimatedVisibility] does not do for you: a button at a tenth of its
 * ink is a button that can still be pressed, and nobody means to press one.
 *
 * ### Where it is the wrong thing
 * There is one transition here and no way to ask for another. Reach for
 * [androidx.compose.animation.AnimatedVisibility] when the size has to animate too — this dissolves
 * in place — and note that un-inked content inside a fade holds full strength unless it asks for the
 * fade with [com.alexgabor.design.riso.risograph.inks.risoFadeAsAlpha].
 *
 * @param animationSpec how the dissolve runs. It has to settle rather than approach, which every
 *   spring and tween here does.
 */
@Composable
fun RisoAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessLow),
    content: @Composable () -> Unit,
) {
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = animationSpec,
        label = "risoFade",
    )
    // Composed while it is on its way in or out, and not a frame longer. The gate is what
    // AnimatedVisibility is otherwise here for, and doing it plainly keeps the content a child of
    // whatever holds it — parent data like `ColumnScope.align` reaches the layout it was meant for
    // rather than stopping at a layout of AnimatedVisibility's own.
    if (visible || fade > 0f) {
        Box(
            modifier
                .risoFade(fade)
                .blockPointerInput(block = !visible),
        ) {
            content()
        }
    }
}
