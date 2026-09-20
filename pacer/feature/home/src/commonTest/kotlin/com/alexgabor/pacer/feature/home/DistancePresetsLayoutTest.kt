package com.alexgabor.pacer.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.pacer.feature.home.slider.DistanceSliderState
import com.alexgabor.pacer.feature.home.slider.PaceSliderState
import com.alexgabor.pacer.feature.home.slider.TimeSliderState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the presets sit, which is the left pane's business and used to be nobody's: the alignment
 * the pane asks for was swallowed by the layout the old transition wrapped them in, and parent data
 * is only read by the layout a composable is a child of.
 */
@OptIn(ExperimentalTestApi::class)
class DistancePresetsLayoutTest {

    private val paneWidth = 400.dp

    @Test
    fun thePresetsSitAtTheEndOfTheColumnThatAsksForIt() = runComposeUiTest {
        setContent {
            RisoTheme {
                Column(Modifier.width(paneWidth)) {
                    DistancePresets(
                        state = pacing(),
                        modifier = Modifier.align(Alignment.End).testTag("presets"),
                    )
                }
            }
        }
        waitForIdle()

        val bounds = onNodeWithTag("presets").getUnclippedBoundsInRoot()
        assertTrue(
            bounds.right.value >= paneWidth.value - 1f,
            "the presets did not reach the end of the pane: ${bounds.right} of $paneWidth",
        )
        assertTrue(bounds.left.value > 0f, "the presets filled the pane rather than aligning in it")
    }

    /** A run with pace computed, which is when the presets are offered. */
    private fun pacing() = PaceCalculatorState.launched(
        DistanceSliderState(),
        PaceSliderState(),
        TimeSliderState(),
        PacerLaunchArgs.None,
    )
}
