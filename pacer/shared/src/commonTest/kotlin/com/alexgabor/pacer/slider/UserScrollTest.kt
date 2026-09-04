package com.alexgabor.pacer.slider

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.alexgabor.pacer.home.collectUserScroll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a slider reports back up to the values, which is only ever what the user's own finger did.
 *
 * Driven through plain flags rather than a real slider: the gesture that sets the flag comes from
 * the scroll modifier, and a [androidx.compose.foundation.lazy.LazyListState] outside a composition
 * refuses to scroll at all — it waits for a first layout that never comes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserScrollTest {

    @Test
    fun aSliderReportsNothingUntilTheUserTouchesIt() = runTest {
        val scrolling = mutableStateOf(false)
        val value = mutableStateOf(0)
        val reported = mutableListOf<Int>()

        val job = launch {
            collectUserScroll({ scrolling.value }, { value.value }, { reported += it })
        }
        advanceUntilIdle()
        // Not even the position the sliders happen to be sitting at: that would overwrite a
        // restored value with whatever an unlaid-out ruler reads.
        assertEquals(emptyList<Int>(), reported)

        Snapshot.withMutableSnapshot { scrolling.value = true }
        advanceUntilIdle()
        assertEquals(listOf(0), reported)

        // Live, so the other two cards move under the finger rather than only at the end.
        Snapshot.withMutableSnapshot { value.value = 5 }
        advanceUntilIdle()
        assertEquals(listOf(0, 5), reported)

        job.cancel()
    }

    @Test
    fun theValueAGestureSettlesOnIsReported() = runTest {
        val scrolling = mutableStateOf(false)
        val value = mutableStateOf(0)
        val reported = mutableListOf<Int>()

        val job = launch {
            collectUserScroll({ scrolling.value }, { value.value }, { reported += it })
        }
        Snapshot.withMutableSnapshot { scrolling.value = true }
        advanceUntilIdle()

        // The scroll that settles a fling and the end of the gesture can land in the same frame.
        // Dropping that last value would leave the field a line off the ruler, and the other
        // direction would then drag the ruler back — a snap-back after every fling.
        Snapshot.withMutableSnapshot {
            value.value = 7
            scrolling.value = false
        }
        advanceUntilIdle()

        assertEquals(7, reported.last())

        job.cancel()
    }

    @Test
    fun aSliderStopsReportingOnceTheGestureEnds() = runTest {
        val scrolling = mutableStateOf(false)
        val value = mutableStateOf(0)
        val reported = mutableListOf<Int>()

        val job = launch {
            collectUserScroll({ scrolling.value }, { value.value }, { reported += it })
        }
        Snapshot.withMutableSnapshot { scrolling.value = true }
        advanceUntilIdle()
        Snapshot.withMutableSnapshot { scrolling.value = false }
        advanceUntilIdle()
        val afterGesture = reported.size

        // This stands in for the other direction driving the slider: it must not come back up as
        // if the user had scrolled it.
        Snapshot.withMutableSnapshot { value.value = 42 }
        advanceUntilIdle()

        assertEquals(afterGesture, reported.size)

        job.cancel()
    }
}
