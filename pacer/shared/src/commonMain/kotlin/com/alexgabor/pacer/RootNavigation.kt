package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
data object PacerRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

internal val PacerNavConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PacerRoute::class, PacerRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
        }
    }
}

@Composable
fun RootNavigation() {
    val backStack = rememberNavBackStack(PacerNavConfiguration, PacerRoute)

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.risoPaper(),
        entryProvider = entryProvider {
            entry<PacerRoute> {
                PacerScreen(onSettingsClick = { backStack.add(SettingsRoute) })
            }
            entry<SettingsRoute> {
                SettingsScreen(onBackClick = { backStack.removeLastOrNull() })
            }
        },
    )
}