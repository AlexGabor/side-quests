package com.alexgabor.design.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Shows the top of [nav]'s back stack, crossfading between screens by thinning their ink — see
 * [rememberRisoFadeNavEntryDecorator].
 */
@Composable
fun <T : NavKey> RisoNavigation(
    nav: DeepLinkedBackStack<T>,
    modifier: Modifier = Modifier,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    val backStack = nav.backStack

    CompositionLocalProvider(LocalDeepLink provides nav.remainder) {
        androidx.navigation3.ui.NavDisplay(
            backStack = backStack,
            modifier = modifier,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberRisoFadeNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
            transitionSpec = { noTransition },
            popTransitionSpec = { noTransition },
            predictivePopTransitionSpec = { noTransition },
        )
    }
}

// The screens fade themselves; see rememberRisoFadeNavEntryDecorator.
private val noTransition = EnterTransition.None togetherWith ExitTransition.None
