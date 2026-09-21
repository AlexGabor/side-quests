package com.alexgabor.design.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.entryProvider
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.components.RisoFadeDefaults
import com.alexgabor.lib.appstateurl.FakeAppUrl
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RisoNavigationTest {

    @Test
    fun openingAScreenCrossfadesAndThenLetsTheOldOneGo() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>
        setContent { nav = navigation() }
        mainClock.autoAdvance = false
        onNodeWithTag("home").assertExists()

        nav.open(Root.Detail("a"))
        mainClock.advanceTimeBy(MID_OVERLAP)
        onNodeWithTag("home").assertExists()
        onNodeWithTag("detail").assertExists()

        mainClock.advanceTimeBy(RisoFadeDefaults.CrossfadeMillis.toLong())
        mainClock.advanceTimeByFrame()
        onNodeWithTag("home").assertDoesNotExist()
        onNodeWithTag("detail").assertExists()
    }

    @Test
    fun goingBackCrossfadesTheSameWay() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>
        setContent { nav = navigation(listOf(Root.Home, Root.Detail("a"))) }
        mainClock.autoAdvance = false
        onNodeWithTag("detail").assertExists()

        nav.back()
        mainClock.advanceTimeBy(MID_OVERLAP)
        onNodeWithTag("home").assertExists()
        onNodeWithTag("detail").assertExists()

        mainClock.advanceTimeBy(RisoFadeDefaults.CrossfadeMillis.toLong())
        mainClock.advanceTimeByFrame()
        onNodeWithTag("detail").assertDoesNotExist()
        onNodeWithTag("home").assertExists()
    }

    @Test
    fun theScreenBeingLeftTakesNoPress() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>
        var detailClicks = 0
        setContent {
            nav = navigation(listOf(Root.Home, Root.Detail("a")), onDetailClick = { detailClicks++ })
        }
        mainClock.autoAdvance = false
        onNodeWithTag("detail").performClick()
        assertEquals(1, detailClicks, "a press did not reach the screen on top")

        // Going back, the screen being left stays on top of the one returning, so nothing but the
        // fade's own blocking stands between it and the press.
        nav.back()
        mainClock.advanceTimeBy(PART_WAY_OUT)
        onNodeWithTag("detail").performClick()
        assertEquals(1, detailClicks, "a press reached the screen that was being left")
    }

    @androidx.compose.runtime.Composable
    private fun navigation(
        initial: List<Root> = listOf(Root.Home),
        onDetailClick: () -> Unit = {},
    ): DeepLinkedBackStack<Root> {
        val nav = rememberDeepLinkedBackStack(
            serializer = Root.serializer(),
            appUrl = remember { FakeAppUrl() },
            screenName = { if (it is Root.Detail) "detail" else "home" },
            keyForScreen = { null },
            initial = { initial },
        )
        RisoTheme {
            RisoNavigation(
                nav = nav,
                entryProvider = entryProvider {
                    entry<Root.Home> {
                        Box(Modifier.fillMaxSize().testTag("home"))
                    }
                    entry<Root.Detail> {
                        Box(Modifier.fillMaxSize().testTag("detail").clickable(onClick = onDetailClick))
                    }
                },
            )
        }
        return nav
    }

    private companion object {
        /** Both sides on the page: the incoming one has started and the outgoing one has not finished. */
        val MID_OVERLAP =
            ((RisoFadeDefaults.InDelayMillis + RisoFadeDefaults.FadeMillis) / 2).toLong()

        /** The outgoing side is fading and the incoming one has not started yet. */
        val PART_WAY_OUT = (RisoFadeDefaults.InDelayMillis / 2).toLong()
    }
}
