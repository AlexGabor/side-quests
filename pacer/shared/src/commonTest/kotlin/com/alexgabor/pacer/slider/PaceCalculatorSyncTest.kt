package com.alexgabor.pacer.slider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import com.alexgabor.design.riso.RisoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TimeTag = "time"
private const val DistanceTag = "distance"
private const val PaceTag = "pace"

/**
 * The sync between the values and the rulers, against a real composition.
 *
 * A ruler has to be laid out before any of this can be exercised: a
 * [androidx.compose.foundation.lazy.LazyListState] outside a composition refuses to scroll at all,
 * because it waits for a first layout that never comes. That wait is also the thing being tested —
 * it is what a slider does while its card is off-screen.
 */
@OptIn(ExperimentalTestApi::class)
class PaceCalculatorSyncTest {

    /**
     * The three sliders and the sync, without the surrounding cards. `userScrollEnabled` mirrors
     * what the cards do: the metric being solved for is the one you can't drag.
     */
    @Composable
    private fun Sliders(state: PaceCalculatorState, showPace: Boolean = true) {
        LaunchedEffect(state) { state.sync() }
        RisoTheme {
            Column(Modifier.fillMaxSize()) {
                TimeSlider(
                    modifier = Modifier.testTag(TimeTag),
                    userScrollEnabled = state.selectedMetric != Metric.Time,
                    state = state.timeSliderState,
                )
                DistanceSlider(
                    modifier = Modifier.testTag(DistanceTag),
                    userScrollEnabled = state.selectedMetric != Metric.Distance,
                    state = state.distanceSliderState,
                )
                if (showPace) {
                    PaceSlider(
                        modifier = Modifier.testTag(PaceTag),
                        userScrollEnabled = state.selectedMetric != Metric.Pace,
                        state = state.paceSliderState,
                    )
                }
            }
        }
    }

    @Test
    fun theSlidersStartOnTheValuesTheyWereGiven() = runComposeUiTest {
        val state = PaceCalculatorState()
        setContent { Sliders(state) }
        waitForIdle()

        // 42.20 km at 6:00/km, which is 4:13:12.
        assertEquals(42, state.distanceSliderState.wholeTrackState.tick)
        assertEquals(20, state.distanceSliderState.fractionTrackState.tick)
        assertEquals(6, state.paceSliderState.minuteTrackState.tick)
        assertEquals(0, state.paceSliderState.secondTrackState.tick)
        assertEquals(4, state.timeSliderState.hourTrackState.tick)
        assertEquals(13, state.timeSliderState.minuteTrackState.tick)
        assertEquals(12, state.timeSliderState.secondTrackState.tick)
    }

    /**
     * What a rotation does: brand new sliders, and values that came back from the saver. The
     * rulers used to carry their own restored scroll offset into a track that had not been
     * measured yet, and read [Int.MAX_VALUE] out of it.
     */
    @Test
    fun restoredValuesPlaceTheSliders() = runComposeUiTest {
        val state = PaceCalculatorState(
            distance = Distance(9.53),
            pace = 26.minutes + 47.seconds,
            time = 4.hours + 15.minutes + 12.seconds,
            selectedMetric = Metric.Pace,
        )
        setContent { Sliders(state) }
        waitForIdle()

        assertEquals(9, state.distanceSliderState.wholeTrackState.tick)
        assertEquals(53, state.distanceSliderState.fractionTrackState.tick)
        assertEquals(26, state.paceSliderState.minuteTrackState.tick)
        assertEquals(47, state.paceSliderState.secondTrackState.tick)
        assertEquals(4, state.timeSliderState.hourTrackState.tick)
        assertEquals(15, state.timeSliderState.minuteTrackState.tick)
        assertEquals(12, state.timeSliderState.secondTrackState.tick)
    }

    @Test
    fun aValuePastTheEndOfItsRulerParksAtTheEnd() = runComposeUiTest {
        val state = PaceCalculatorState(pace = 2.hours)
        setContent { Sliders(state) }
        waitForIdle()

        assertEquals(Comparison.Greater, state.paceComparison)
        assertEquals(
            state.paceSliderState.minuteTrackState.maxTick,
            state.paceSliderState.minuteTrackState.tick,
        )
        assertEquals(59, state.paceSliderState.secondTrackState.tick)
        // And the value itself is untouched, so coming back into range shows the real figure.
        assertEquals(2.hours, state.pace)
    }

    /**
     * A card scrolled out of the list is disposed, and one that has never been on screen has never
     * been measured. Either way its slider has no pixel grid to place a value on, so it waits —
     * and then lands on whatever the value has become by the time it appears, not on the one it
     * was waiting for.
     */
    @Test
    fun aSliderThatHasNeverBeenComposedLandsOnTheLatestValue() = runComposeUiTest {
        val state = PaceCalculatorState(
            distance = Distance(10.0),
            pace = 5.minutes,
            time = 50.minutes,
            selectedMetric = Metric.Pace,
        )
        var showPace by mutableStateOf(false)
        setContent { Sliders(state, showPace = showPace) }
        waitForIdle()

        assertEquals(0, state.paceSliderState.minuteTrackState.tick)

        // 10 km in 25 minutes is 2:30/km — a pace the absent slider never saw.
        state.onTimeScrolled(25.minutes)
        waitForIdle()

        showPace = true
        waitForIdle()

        assertEquals(2, state.paceSliderState.minuteTrackState.tick)
        assertEquals(30, state.paceSliderState.secondTrackState.tick)
    }

    /** A drag on one slider becomes a value, and the other two follow it. */
    @Test
    fun draggingASliderMovesTheOthers() = runComposeUiTest {
        val state = PaceCalculatorState(
            distance = Distance(10.0),
            pace = 5.minutes,
            time = 50.minutes,
            selectedMetric = Metric.Pace,
        )
        setContent { Sliders(state) }
        waitForIdle()

        val timeBefore = state.time
        onNodeWithTag(TimeTag).performTouchInput { swipeLeft() }
        waitForIdle()

        // Left is forwards, so the time went up and the pace with it.
        assertTrue(state.time > timeBefore, "expected the drag to move the time, still ${state.time}")
        assertEquals(state.time / 10.0, state.pace)
        assertEquals(PaceSliderState.seconds(state.pace) / 60, state.paceSliderState.minuteTrackState.tick)
        assertEquals(PaceSliderState.seconds(state.pace) % 60, state.paceSliderState.secondTrackState.tick)
        // The distance is an input here, so nothing should have moved it.
        assertEquals(10.0, state.distance.kilometers)
    }

    /** The slider being solved for can't be dragged, so it never reports anything back. */
    @Test
    fun theSelectedMetricsSliderDoesNotAcceptADrag() = runComposeUiTest {
        val state = PaceCalculatorState(
            distance = Distance(10.0),
            pace = 5.minutes,
            time = 50.minutes,
            selectedMetric = Metric.Pace,
        )
        setContent { Sliders(state) }
        waitForIdle()

        onNodeWithTag(PaceTag).performTouchInput { swipeLeft() }
        waitForIdle()

        assertEquals(5.minutes, state.pace)
        assertEquals(50.minutes, state.time)
    }
}
