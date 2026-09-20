package com.alexgabor.design.riso.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RisoAnimatedVisibilityTest {

    private val slow = tween<Float>(durationMillis = 1_000)

    @Test
    fun contentStaysUntilTheDissolveSettlesAndThenLeaves() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var composed = false
        setContent {
            RisoTheme {
                RisoAnimatedVisibility(visible = visible, animationSpec = slow) {
                    composed = true
                    Box(Modifier.size(48.dp).testTag("content"))
                }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("content").assertIsDisplayed()

        visible = false
        mainClock.advanceTimeBy(300)
        // Part-way out: still printing, still in the layout, so nothing beside it has moved.
        onNodeWithTag("content").assertIsDisplayed()

        composed = false
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("content").assertDoesNotExist()
        assertTrue(!composed, "the content was still being composed once it had gone")
    }

    @Test
    fun contentOnItsWayOutDoesNotTakeAPress() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var clicks = 0
        setContent {
            RisoTheme {
                RisoAnimatedVisibility(visible = visible, animationSpec = slow) {
                    Box(Modifier.size(48.dp).testTag("content").clickable { clicks++ })
                }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("content").performClick()
        assertEquals(1, clicks, "a press did not reach content that was fully printed")

        visible = false
        mainClock.advanceTimeBy(300)
        onNodeWithTag("content").performClick()
        assertEquals(1, clicks, "a press reached content that was on its way out")
    }

    @Test
    fun hiddenContentIsNotComposedAtAll() = runComposeUiTest {
        var composed = false
        setContent {
            RisoTheme {
                RisoAnimatedVisibility(visible = false) {
                    composed = true
                    Box(Modifier.size(48.dp).testTag("content"))
                }
            }
        }
        waitForIdle()
        onNodeWithTag("content").assertDoesNotExist()
        assertTrue(!composed, "hidden content was composed")
    }
}
