package com.alexgabor.design.riso.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BottomSheetTest {

    @Test
    fun anOpenSheetShowsItsContentAndHandle() = runComposeUiTest {
        setContent {
            RisoTheme(effectsEnabled = false) {
                BottomSheet(onDismissRequest = {}) {
                    Body(text = "Speed")
                }
            }
        }
        onNodeWithText("Speed").assertExists()
        onNodeWithContentDescription("Drag handle").assertExists()
    }

    @Test
    fun aTapOutsideDismissesTheSheetAndNeverReachesThePage() = runComposeUiTest {
        var open by mutableStateOf(true)
        var dismissed = 0
        var pagePresses = 0
        setContent {
            RisoTheme(effectsEnabled = false) {
                Box(Modifier.fillMaxSize().clickable { pagePresses++ })
                if (open) {
                    BottomSheet(onDismissRequest = { dismissed++; open = false }) {
                        Body(text = "Speed", modifier = Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        }
        // The sheet's window covers the page; its top-left corner is outside the sheet.
        onNode(isDialog()).performTouchInput { click(Offset(10f, 10f)) }
        waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(0, pagePresses)
        onNodeWithText("Speed").assertDoesNotExist()
    }

    @Test
    fun contentDismissesItsSheetOnce() = runComposeUiTest {
        var open by mutableStateOf(true)
        var dismissed = 0
        setContent {
            RisoTheme(effectsEnabled = false) {
                if (open) {
                    BottomSheet(onDismissRequest = { dismissed++; open = false }) {
                        Body(
                            text = "Chapter 3",
                            modifier = Modifier.clickable {
                                dismiss()
                                dismiss()
                            },
                        )
                    }
                }
            }
        }
        onNodeWithText("Chapter 3").performClick()
        waitForIdle()

        assertEquals(1, dismissed)
        onNodeWithText("Chapter 3").assertDoesNotExist()
    }

    @Test
    fun thePagePrintsLighterInProportionToTheSheet() {
        assertEquals(1f, backdropFade(0f))
        assertEquals(BottomSheetDefaults.BackdropFade, backdropFade(1f), 1e-6f)
        assertEquals((1f + BottomSheetDefaults.BackdropFade) / 2, backdropFade(0.5f), 1e-6f)
    }

    @Test
    fun theBackdropFollowsTheMostOpenSheetAndForgetsClosedOnes() {
        val backdrop = RisoBackdrop()
        assertEquals(1f, backdrop.fade)
        backdrop.report("speed", 0.5f)
        backdrop.report("chapters", 1f)
        assertEquals(BottomSheetDefaults.BackdropFade, backdrop.fade, 1e-6f)
        backdrop.remove("chapters")
        assertEquals(backdropFade(0.5f), backdrop.fade)
        backdrop.remove("speed")
        assertEquals(1f, backdrop.fade)
    }
}
