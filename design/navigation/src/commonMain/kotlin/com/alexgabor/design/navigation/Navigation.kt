package com.alexgabor.design.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

@Composable
fun <T : NavKey> RisoNavigation(
    backStack: NavBackStack<T>,
    modifier: Modifier = Modifier,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    androidx.navigation3.ui.NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryProvider = entryProvider,
        transitionSpec = { defaultTransition },
        popTransitionSpec = { defaultPopTransition },
        predictivePopTransitionSpec = { defaultPopTransition },
    )
}

private val defaultPopTransition =
    slideInHorizontally(initialOffsetX = { -it }) togetherWith
            slideOutHorizontally(targetOffsetX = { it })

private val defaultTransition =
    slideInHorizontally(initialOffsetX = { it }) togetherWith
        slideOutHorizontally(targetOffsetX = { -it })