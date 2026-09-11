package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.alexgabor.design.navigation.RisoNavigation
import com.alexgabor.design.navigation.rememberDeepLinkedBackStack
import com.alexgabor.design.riso.risograph.paper.risoPaper
import com.alexgabor.pacer.feature.home.DistanceUnit
import com.alexgabor.pacer.feature.home.Metric
import com.alexgabor.pacer.feature.home.PacerLaunchArgs
import com.alexgabor.pacer.feature.home.PacerScreen
import com.alexgabor.pacer.feature.home.rememberPaceCalculatorState
import com.alexgabor.pacer.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
internal sealed interface RootDestination : NavKey {

    @Serializable
    data class Pacer(
        val distance: Double? = null,
        val paceMillis: Long? = null,
        val timeMillis: Long? = null,
        val metric: String? = null,
        val unit: String? = null,
    ) : RootDestination

    @Serializable
    data object Settings : RootDestination
}

private fun RootDestination.Pacer.launchArgs() = PacerLaunchArgs(
    distance = distance,
    pace = paceMillis?.milliseconds,
    time = timeMillis?.milliseconds,
    // By name, falling back rather than throwing, so a key from an older build degrades to a default.
    metric = metric?.let { name -> Metric.entries.firstOrNull { it.name == name } },
    unit = unit?.let { name -> DistanceUnit.entries.firstOrNull { it.name == name } },
)

@Composable
fun RootNavigation() {
    val nav = rememberDeepLinkedBackStack(RootDestination.serializer()) {
        listOf(RootDestination.Pacer())
    }
    RisoNavigation(
        nav = nav,
        modifier = Modifier.risoPaper(),
        entryProvider = entryProvider {
            // Content keys are spelled out so that entry identity — and with it the saved state
            // of the screen — is not tied to the launch arguments the key happens to carry.
            entry<RootDestination.Pacer>(clazzContentKey = { "pacer" }) { key ->
                PacerScreen(
                    state = rememberPaceCalculatorState(key.launchArgs()),
                    onSettingsClick = { nav.backStack.add(RootDestination.Settings) },
                )
            }
            entry<RootDestination.Settings>(clazzContentKey = { "settings" }) {
                SettingsScreen(onBackClick = { nav.backStack.removeLastOrNull() })
            }
        },
    )
}
