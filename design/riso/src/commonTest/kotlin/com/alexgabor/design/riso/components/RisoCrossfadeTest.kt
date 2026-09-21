package com.alexgabor.design.riso.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RisoCrossfadeTest {

    @Test
    fun bothSidesAreOnThePageUntilTheSwapSettles() = runComposeUiTest {
        var target by mutableStateOf("a")
        val composed = mutableSetOf<String>()
        setContent {
            RisoTheme {
                RisoCrossfade(targetState = target) { state ->
                    composed += state
                    Box(Modifier.size(48.dp).testTag(state))
                }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("a").assertExists()

        target = "b"
        mainClock.advanceTimeBy(MID_OVERLAP)
        onNodeWithTag("a").assertExists()
        onNodeWithTag("b").assertExists()

        mainClock.advanceTimeBy(RisoFadeDefaults.CrossfadeMillis.toLong())
        composed.clear()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("a").assertDoesNotExist()
        onNodeWithTag("b").assertExists()
        assertTrue("a" !in composed, "the old content was still being composed once it had gone")
    }

    @Test
    fun contentOnItsWayOutDoesNotTakeAPress() = runComposeUiTest {
        var target by mutableStateOf("a")
        val clicks = mutableMapOf<String, Int>()
        setContent {
            RisoTheme {
                RisoCrossfade(targetState = target) { state ->
                    // Apart, so a press at one never lands on the other, which prints on top.
                    Box(
                        Modifier.padding(start = if (state == "b") 96.dp else 0.dp)
                            .size(48.dp).testTag(state)
                            .clickable { clicks[state] = (clicks[state] ?: 0) + 1 },
                    )
                }
            }
        }
        mainClock.autoAdvance = false

        target = "b"
        mainClock.advanceTimeBy(PART_WAY_OUT)
        onNodeWithTag("a").performClick()
        assertEquals(null, clicks["a"], "a press reached content that was on its way out")

        mainClock.advanceTimeBy(RisoFadeDefaults.CrossfadeMillis.toLong())
        onNodeWithTag("b").performClick()
        assertEquals(1, clicks["b"], "a press did not reach the content that had arrived")
    }

    @Test
    fun firstCompositionShowsOnlyTheTarget() = runComposeUiTest {
        val composed = mutableListOf<String>()
        setContent {
            RisoTheme {
                RisoCrossfade(targetState = "a") { state ->
                    composed += state
                    Box(Modifier.size(48.dp).testTag(state))
                }
            }
        }
        waitForIdle()
        onAllNodesWithTag("a").assertCountEquals(1)
        assertEquals(setOf("a"), composed.toSet())
    }

    @Test
    fun changingBackMidWayKeepsOneCopy() = runComposeUiTest {
        var target by mutableStateOf("a")
        setContent {
            RisoTheme {
                RisoCrossfade(targetState = target) { state ->
                    Box(Modifier.size(48.dp).testTag(state))
                }
            }
        }
        mainClock.autoAdvance = false

        target = "b"
        mainClock.advanceTimeBy(PART_WAY_OUT)
        target = "a"
        mainClock.advanceTimeBy(PART_WAY_OUT)
        onAllNodesWithTag("a").assertCountEquals(1)

        mainClock.advanceTimeBy(RisoFadeDefaults.CrossfadeMillis.toLong() * 2)
        onAllNodesWithTag("a").assertCountEquals(1)
        onNodeWithTag("b").assertDoesNotExist()
    }

    private companion object {
        /** Both sides on the page: the incoming one has started and the outgoing one has not finished. */
        val MID_OVERLAP =
            ((RisoFadeDefaults.InDelayMillis + RisoFadeDefaults.FadeMillis) / 2).toLong()

        /** The outgoing side is fading and the incoming one has not started yet. */
        val PART_WAY_OUT = (RisoFadeDefaults.InDelayMillis / 2).toLong()
    }
}
