package com.alexgabor.pacer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.design.navigation.LocalDeepLink
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.launch.LaunchParameters
import com.alexgabor.pacer.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test

private class FakeSettingsRepository : SettingsRepository {
    override val risoEffectsEnabled: Flow<Boolean> = flowOf(false)
    override suspend fun setRisoEffects(enabled: Boolean) = Unit
}

/** The whole chain, from what the app was launched with to what is on screen. */
@OptIn(ExperimentalTestApi::class)
class RootNavigationTest {

    @AfterTest
    fun tearDown() = stopKoin()

    private fun ComposeUiTest.launchedWith(query: String) {
        // Settings reaches the screen through Koin, so there has to be one to inject.
        startKoin {
            modules(module { single<SettingsRepository> { FakeSettingsRepository() } })
        }

        setContent {
            RisoTheme(effectsEnabled = false) {
                CompositionLocalProvider(
                    LocalDeepLink provides pacerDeepLink(LaunchParameters.ofQueryString(query)),
                ) {
                    RootNavigation()
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun aPlainLaunchOpensPacer() = runComposeUiTest {
        launchedWith("")

        onNodeWithText("Pacer").assertIsDisplayed()
    }

    @Test
    fun aDeepLinkIntoSettingsOpensSettings() = runComposeUiTest {
        launchedWith("screen=settings")

        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun aDeepLinkedRunIsOnTheCards() = runComposeUiTest {
        launchedWith("distance=10&pace=5:00")

        // The cards read "Distance = 10.00 km" and so on, hence the substring match.
        onNodeWithText("10.00 km", substring = true).assertIsDisplayed()
        onNodeWithText("5:00 min/km", substring = true).assertIsDisplayed()
        // Time is the one left out, so it is the one worked out.
        onNodeWithText("0h 50m 00s", substring = true).assertIsDisplayed()
    }
}
