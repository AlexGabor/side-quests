package com.alexgabor.design.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.alexgabor.design.riso.components.RisoFadeDefaults
import com.alexgabor.design.riso.risograph.inks.risoFade

/**
 * Crossfades screens the way `RisoCrossfade` swaps content: the screen being left thins its ink
 * away as the one being opened prints in, on [RisoFadeDefaults]' lingering curve.
 *
 * Not `RisoCrossfade` itself, because the transition is not ours to run. `NavDisplay` owns it, and
 * it only takes enter and exit transitions — layer effects, which would wash the print out rather
 * than thin it. So `NavDisplay` is given no transition at all, and each entry reads the one it is
 * already part of and runs its own fade on it. Animated on that transition, the fade is something
 * `NavDisplay` waits for before it lets the old screen go, and something predictive back seeks
 * along with the gesture.
 */
@Composable
internal fun <T : Any> rememberRisoFadeNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator { entry ->
        val transition = LocalNavAnimatedContentScope.current.transition
        val fade by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) RisoFadeDefaults.crossfadeIn
                else RisoFadeDefaults.crossfadeOut
            },
            label = "risoNavigationFade",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val leaving = transition.targetState != EnterExitState.Visible

        Box(
            Modifier
                .fillMaxSize()
                .risoFade(fade)
                .pointerInput(leaving) {
                    if (!leaving) return@pointerInput
                    // A screen on its way out is still clickable as far as it knows. Taken before
                    // it sees them, so nothing on it can be pressed.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes
                                .forEach { it.consume() }
                        }
                    }
                },
        ) {
            entry.Content()
        }
    }
}
