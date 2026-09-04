package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.alexgabor.design.navigation.RisoNavigation
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlinx.serialization.Serializable

@Serializable
private sealed interface RootDestination : NavKey {

    @Serializable
    data object Pacer : RootDestination

    @Serializable
    data object Settings : RootDestination
}

@Composable
fun RootNavigation() {
    val backStack = rememberSerializable(
        serializer = NavBackStackSerializer(RootDestination.serializer())
    ) {
        NavBackStack(RootDestination.Pacer)
    }

    RisoNavigation(
        backStack = backStack,
        modifier = Modifier.risoPaper(),
        entryProvider = entryProvider {
            entry<RootDestination.Pacer> {
                PacerScreen(onSettingsClick = { backStack.add(RootDestination.Settings) })
            }
            entry<RootDestination.Settings> {
                SettingsScreen(onBackClick = { backStack.removeLastOrNull() })
            }
        },
    )
}
