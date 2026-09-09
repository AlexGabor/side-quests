package com.alexgabor.pacer.home

import com.alexgabor.pacer.home.slider.DistanceSliderState
import com.alexgabor.pacer.home.slider.PaceSliderState
import com.alexgabor.pacer.home.slider.TimeSliderState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class PaceCalculatorLaunchTest {

    private fun launched(args: PacerLaunchArgs) = PaceCalculatorState.launched(
        DistanceSliderState(),
        PaceSliderState(),
        TimeSliderState(),
        args,
    )

    @Test
    fun nothingLaunchedWithLeavesTheDefaultsExactlyAsTheyAre() {
        val state = launched(PacerLaunchArgs.None)

        assertEquals("42.20 km", state.displayedDistance)
        assertEquals("6:00 min/km", state.displayedPace)
        assertEquals("4h 13m 12s", state.displayedTime)
        assertEquals(Metric.Pace, state.selectedMetric)
        assertEquals(DistanceUnit.Kilometers, state.selectedUnit)
    }

    // Naming exactly two of the three reads as "work out the third".

    @Test
    fun aDistanceAndAPaceWorkOutTheTime() {
        val state = launched(PacerLaunchArgs(distance = 10.0, pace = 5.minutes))

        assertEquals(Metric.Time, state.selectedMetric)
        assertEquals("0h 50m 00s", state.displayedTime)
    }

    @Test
    fun aDistanceAndATimeWorkOutThePace() {
        val state = launched(PacerLaunchArgs(distance = 10.0, time = 50.minutes))

        assertEquals(Metric.Pace, state.selectedMetric)
        assertEquals("5:00 min/km", state.displayedPace)
    }

    @Test
    fun aPaceAndATimeWorkOutTheDistance() {
        val state = launched(PacerLaunchArgs(pace = 5.minutes, time = 50.minutes))

        assertEquals(Metric.Distance, state.selectedMetric)
        assertEquals("10.00 km", state.displayedDistance)
    }

    @Test
    fun anExplicitMetricWinsOverWhatWasLeftOut() {
        val state = launched(
            PacerLaunchArgs(distance = 10.0, pace = 5.minutes, metric = Metric.Distance),
        )

        assertEquals(Metric.Distance, state.selectedMetric)
        // Distance is now the computed one, so it follows the default time rather than the 10 given.
        assertEquals("50.64 km", state.displayedDistance)
    }

    @Test
    fun allThreeStillAddUp() {
        // The selected metric is always the computed one, so a pace that contradicts the other two
        // is corrected rather than kept.
        val state = launched(
            PacerLaunchArgs(distance = 10.0, pace = 9.minutes, time = 50.minutes),
        )

        assertEquals(Metric.Pace, state.selectedMetric)
        assertEquals("5:00 min/km", state.displayedPace)
    }

    @Test
    fun oneValueIsReadAgainstTheDefaults() {
        val state = launched(PacerLaunchArgs(distance = 10.0))

        assertEquals(Metric.Pace, state.selectedMetric)
        assertEquals("10.00 km", state.displayedDistance)
        assertEquals("4h 13m 12s", state.displayedTime)
    }

    @Test
    fun milesAreReadAsMiles() {
        val state = launched(
            PacerLaunchArgs(distance = 10.0, pace = 5.minutes, unit = DistanceUnit.Miles),
        )

        assertEquals(DistanceUnit.Miles, state.selectedUnit)
        assertEquals("10.00 mi", state.displayedDistance)
        assertEquals("5:00 min/mi", state.displayedPace)
        // Ten miles at five minutes a mile, whatever the calculator holds underneath.
        assertEquals("0h 50m 00s", state.displayedTime)
        assertEquals(Distance.of(10.0, DistanceUnit.Miles).kilometers, state.distance.kilometers)
    }

    @Test
    fun aUnitOnItsOwnDoesNotDisturbTheDefaults() {
        val state = launched(PacerLaunchArgs(unit = DistanceUnit.Miles))

        assertEquals(DistanceUnit.Miles, state.selectedUnit)
        assertEquals(PaceCalculatorState.DefaultTime, state.time)
        assertEquals(PaceCalculatorState.DefaultDistance.kilometers, state.distance.kilometers)
        // Recomputing a consistent default lands back on it exactly rather than rounding it away.
        assertEquals(PaceCalculatorState.DefaultPace, state.pace)
        // Only the reading changes with the unit: six minutes a kilometre is 9:39 a mile.
        assertEquals("9:39 min/mi", state.displayedPace)
    }
}
