package com.alexgabor.pacer.feature.home

import androidx.compose.runtime.snapshots.Snapshot
import com.alexgabor.lib.appstateurl.FakeAppUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** What the calculator tells the address it is at, and how it says it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettledRunReachesTheUrlTest {

    private val appUrl = FakeAppUrl()

    private fun TestScope.writing(state: PaceCalculatorState) {
        backgroundScope.launch { state.writeSettledRunsTo { appUrl } }
        waitOutTheSettle()
    }

    /** The collector lives in `backgroundScope`, whose timers `advanceUntilIdle` doesn't wait for. */
    private fun TestScope.waitOutTheSettle() {
        runCurrent()
        advanceTimeBy(SettleDelay + 1.milliseconds)
    }

    @Test
    fun theRunTheCalculatorOpenedOnIsNotWrittenDown() = runTest {
        val state = PaceCalculatorState(distance = Distance(10.0), pace = 5.minutes)

        writing(state)

        // It is what the address already says, so there is nothing to tell it.
        assertEquals(emptyList(), appUrl.writes)
    }

    @Test
    fun aRunTheCalculatorSettlesOnIsWrittenToTheAddress() = runTest {
        val state = PaceCalculatorState(
            distance = Distance(10.0),
            pace = 5.minutes,
            time = 50.minutes,
            selectedMetric = Metric.Pace,
        )
        writing(state)

        Snapshot.withMutableSnapshot { state.selectMetric(Metric.Distance) }
        waitOutTheSettle()

        assertEquals(
            listOf(
                mapOf(
                    "distance" to "10.00",
                    "pace" to "5:00",
                    "time" to "50:00",
                    "metric" to "distance",
                    "unit" to "kilometers",
                ),
            ),
            appUrl.replaced.map { it.asMap() },
        )
        // A run is what the user is adjusting, not somewhere they went.
        assertEquals(emptyList(), appUrl.pushed)
    }
}
