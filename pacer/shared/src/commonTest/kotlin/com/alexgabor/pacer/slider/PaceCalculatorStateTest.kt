package com.alexgabor.pacer.slider

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PaceCalculatorStateTest {

    // The metric that is selected is the one computed from the other two.

    @Test
    fun paceFollowsDistanceAndTime() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)

        state.onDistanceScrolled(10.0)
        state.onTimeScrolled(50.minutes)

        assertEquals("5:00 min/km", state.displayedPace)
    }

    @Test
    fun timeFollowsDistanceAndPace() {
        val state = PaceCalculatorState(selectedMetric = Metric.Time)

        state.onDistanceScrolled(10.0)
        state.onPaceScrolled(5.minutes)

        assertEquals("0h 50m 00s", state.displayedTime)
    }

    @Test
    fun distanceFollowsTimeAndPace() {
        val state = PaceCalculatorState(selectedMetric = Metric.Distance)

        state.onTimeScrolled(50.minutes)
        state.onPaceScrolled(5.minutes)

        assertEquals("10.00 km", state.displayedDistance)
    }

    @Test
    fun scrollingTheSelectedMetricIsIgnored() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        val before = state.pace

        state.onPaceScrolled(9.minutes)

        assertEquals(before, state.pace)
    }

    // Dividing by a zero the user can scroll to. Both of these used to be a null from the computed
    // properties; getting them wrong now means an infinite Duration or a NaN that throws on round.

    @Test
    fun zeroDistanceLeavesThePaceAlone() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        val before = state.pace

        state.onDistanceScrolled(0.0)

        assertEquals(before, state.pace)
        assertTrue(state.pace.isFinite())
    }

    @Test
    fun zeroPaceLeavesTheDistanceAlone() {
        val state = PaceCalculatorState(selectedMetric = Metric.Distance)
        val before = state.distance

        state.onPaceScrolled(Duration.ZERO)

        assertEquals(before, state.distance)
    }

    // Values are held in kilometres, so a change of unit is lossless however often it happens.

    @Test
    fun distanceSurvivesAUnitRoundTrip() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        state.onDistanceScrolled(10.0)

        state.selectUnit(DistanceUnit.Miles)
        assertEquals("6.21 mi", state.displayedDistance)

        state.selectUnit(DistanceUnit.Kilometers)
        assertEquals("10.00 km", state.displayedDistance)
        assertEquals(10.0, state.distance.kilometers)
    }

    @Test
    fun paceSurvivesAUnitRoundTrip() {
        val state = PaceCalculatorState(selectedMetric = Metric.Time)
        state.onPaceScrolled(5.minutes)

        state.selectUnit(DistanceUnit.Miles)
        assertEquals("8:03 min/mi", state.displayedPace)

        state.selectUnit(DistanceUnit.Kilometers)
        assertEquals("5:00 min/km", state.displayedPace)
        assertEquals(5.minutes, state.pace)
    }

    // Past the end of a ruler the value is kept and the card says so.

    @Test
    fun aPacePastTheEndOfItsRulerIsKeptAndMarked() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        state.onDistanceScrolled(1.0)
        state.onTimeScrolled(2.hours)

        assertEquals(Comparison.Greater, state.paceComparison)
        assertEquals("59:59 min/km", state.displayedPace)
        assertEquals(2.hours, state.pace)
    }

    @Test
    fun comingBackIntoRangeRestoresTheRealValue() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        state.onDistanceScrolled(1.0)
        state.onTimeScrolled(2.hours)

        state.onTimeScrolled(5.minutes)

        assertEquals(Comparison.Equal, state.paceComparison)
        assertEquals("5:00 min/km", state.displayedPace)
    }

    @Test
    fun theLastLineOfARulerIsNotOutOfRange() {
        val state = PaceCalculatorState(selectedMetric = Metric.Pace)
        state.onDistanceScrolled(1.0)
        state.onTimeScrolled(PaceSliderState.MaxTicks.seconds)

        assertEquals(Comparison.Equal, state.paceComparison)
        assertEquals("59:59 min/km", state.displayedPace)
    }

    // Saving.

    @Test
    fun theStateSurvivesASaveAndRestore() {
        val saver = paceCalculatorStateSaver(
            DistanceSliderState(),
            PaceSliderState(),
            TimeSliderState(),
        )
        val state = PaceCalculatorState(
            distance = Distance(10.0),
            // Not a whole number of seconds: a pace scrolled in miles never is, once it is held
            // per kilometre.
            pace = 300123.milliseconds,
            time = 50.minutes,
            selectedMetric = Metric.Time,
            selectedUnit = DistanceUnit.Miles,
        )

        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(state) }
        val restored = saver.restore(saved!!)!!

        assertEquals(10.0, restored.distance.kilometers)
        assertEquals(300123.milliseconds, restored.pace)
        assertEquals(50.minutes, restored.time)
        assertEquals(Metric.Time, restored.selectedMetric)
        assertEquals(DistanceUnit.Miles, restored.selectedUnit)
    }

    @Test
    fun anUnknownEnumNameRestoresToTheDefault() {
        val saver = paceCalculatorStateSaver(
            DistanceSliderState(),
            PaceSliderState(),
            TimeSliderState(),
        )

        val restored = saver.restore(listOf(10.0, 300_000L, 3_000_000L, "Nope", "Nope"))!!

        assertEquals(Metric.Pace, restored.selectedMetric)
        assertEquals(DistanceUnit.Kilometers, restored.selectedUnit)
    }

    // The quantisers, which every number on screen goes through.

    @Test
    fun distanceQuantisesOntoTheRulersGrid() {
        assertEquals(1000, DistanceSliderState.hundredths(10.0))
        assertEquals(543, DistanceSliderState.hundredths(5.426))
        assertEquals(0, DistanceSliderState.hundredths(-1.0))
        assertEquals(0, DistanceSliderState.hundredths(Double.NaN))
        assertEquals(DistanceSliderState.MaxTicks, DistanceSliderState.hundredths(10_000.0))
    }

    @Test
    fun ticksAreNotClampedSoTheCardCanTellItIsPastTheEnd() {
        assertTrue(DistanceSliderState.ticks(10_000.0) > DistanceSliderState.MaxTicks)
        assertTrue(PaceSliderState.ticks(2.hours) > PaceSliderState.MaxTicks)
        assertTrue(TimeSliderState.ticks(200.hours) > TimeSliderState.MaxTicks)
    }

    @Test
    fun anInfinitePaceDoesNotThrow() {
        assertEquals(Int.MAX_VALUE, PaceSliderState.ticks(Duration.INFINITE))
        assertEquals(PaceSliderState.MaxTicks, PaceSliderState.seconds(Duration.INFINITE))
    }

    // Formatting, which is fixed-width and so leans on twoDigits.

    @Test
    fun readoutsAreZeroPadded() {
        val state = PaceCalculatorState(
            distance = Distance(0.05),
            pace = 5.minutes,
            time = 7.seconds,
        )

        assertEquals("0.05 km", state.displayedDistance)
        assertEquals("5:00 min/km", state.displayedPace)
        assertEquals("0h 00m 07s", state.displayedTime)
    }
}
