package com.alexgabor.design.navigation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.alexgabor.lib.appstateurl.FakeAppUrl
import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface Screen : NavKey {
    data object Home : Screen
    data object List : Screen
    data object Item : Screen
}

private fun nameOf(screen: Screen): String = when (screen) {
    Screen.Home -> "home"
    Screen.List -> "list"
    Screen.Item -> "item"
}

private fun screenNamed(name: String): Screen? = when (name) {
    "home" -> Screen.Home
    "list" -> Screen.List
    "item" -> Screen.Item
    else -> null
}

/**
 * The back stack and the app's address, in both directions — without a composition, because
 * neither direction has anything to do with what is drawn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkedBackStackTest {

    private val appUrl = FakeAppUrl()

    /** A launch: the address and the back stack agree, because one was built from the other. */
    private fun TestScope.navigationOn(vararg screens: Screen): DeepLinkedBackStack<Screen> {
        appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to nameOf(screens.last()))))
        val nav = DeepLinkedBackStack(
            backStack = NavBackStack(*screens),
            remainder = DeepLink.Empty,
            appUrl = appUrl,
            screenName = ::nameOf,
            keyForScreen = ::screenNamed,
        )
        backgroundScope.launch { nav.sync() }
        runCurrent()
        return nav
    }

    /** Snapshot state read by a flow only reports what an applied snapshot wrote. */
    private fun TestScope.change(block: () -> Unit) {
        Snapshot.withMutableSnapshot(block)
        runCurrent()
    }

    @Test
    fun theScreenTheAppOpensOnIsNotWrittenDown() = runTest {
        navigationOn(Screen.Home)

        // It is already the address, and writing it would put an entry in front of wherever the
        // user came from.
        assertEquals(emptyList(), appUrl.writes)
    }

    @Test
    fun navigatingIsSomewhereToComeBackTo() = runTest {
        val nav = navigationOn(Screen.Home)

        change { nav.open(Screen.List) }

        assertEquals(listOf(mapOf(ScreenParameter to "list")), appUrl.pushed.map { it.asMap() })
        assertEquals(emptyList(), appUrl.replaced)
    }

    @Test
    fun theAppsOwnBackIsTheSameMoveAsTheBrowsersBack() = runTest {
        val nav = navigationOn(Screen.Home)
        change { nav.open(Screen.List) }

        change { nav.back() }

        // Asked of the address, not done behind it, so the screen left isn't stranded ahead of
        // the user in the history.
        assertEquals(1, appUrl.backs)
        assertEquals(listOf(Screen.Home), nav.backStack.toList())
        assertEquals(listOf(mapOf(ScreenParameter to "list")), appUrl.pushed.map { it.asMap() })
    }

    @Test
    fun goingBackWithNowhereToGoStillLeavesTheScreen() = runTest {
        // Opened straight into a screen: the address has nothing of its own behind it, and going
        // back there would leave the app altogether.
        val nav = navigationOn(Screen.Home, Screen.List)

        change { nav.back() }

        assertEquals(1, appUrl.backs)
        assertEquals(listOf(Screen.Home), nav.backStack.toList())
    }

    @Test
    fun anAddressChangeOpensTheScreenItNames() = runTest {
        val nav = navigationOn(Screen.Home)

        change { appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to "list"))) }

        assertEquals(listOf(Screen.Home, Screen.List), nav.backStack.toList())
    }

    @Test
    fun anAddressNamingAScreenAlreadyBehindUsGoesBackToIt() = runTest {
        val nav = navigationOn(Screen.Home, Screen.List, Screen.Item)

        // Two entries back at once, which a long press on Back does.
        change { appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to "home"))) }

        assertEquals(listOf(Screen.Home), nav.backStack.toList())
    }

    @Test
    fun anAddressNamingNoScreenMeansTheOneUnderneathEverything() = runTest {
        val nav = navigationOn(Screen.Home, Screen.List)

        // What Back lands on when the app was opened by a link that named no screen.
        change { appUrl.moveTo(LaunchParameters(mapOf("id" to "7"))) }

        assertEquals(listOf(Screen.Home), nav.backStack.toList())
        // Pushing here would throw away the entry ahead, and Forward would stop working.
        assertEquals(emptyList(), appUrl.writes)
    }

    @Test
    fun anAddressNamingAScreenThisLevelDoesNotKnowIsLeftAlone() = runTest {
        val nav = navigationOn(Screen.Home)

        change { appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to "elsewhere"))) }

        assertEquals(listOf(Screen.Home), nav.backStack.toList())
    }

    @Test
    fun followingTheAddressIsNotNavigating() = runTest {
        navigationOn(Screen.Home)

        change { appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to "list"))) }

        // Writing it back would be a second entry for one move, and the two directions would then
        // have each other to answer.
        assertEquals(emptyList(), appUrl.writes)
    }

    @Test
    fun aScreenNameIsMatchedWithoutRegardToCase() = runTest {
        val nav = navigationOn(Screen.Home)

        change { appUrl.moveTo(LaunchParameters(mapOf(ScreenParameter to "List"))) }

        assertEquals(listOf(Screen.Home, Screen.List), nav.backStack.toList())
    }
}
