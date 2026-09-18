package com.alexgabor.pacer.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class DistancePresetTest {

    @Test
    fun kilometresOfferTheKilometreRacesAndTheMarathons() {
        assertEquals(
            listOf("5K", "10K", "HM", "M"),
            DistancePreset.forUnit(DistanceUnit.Kilometers).map { it.text },
        )
    }

    @Test
    fun milesOfferTheMileRacesAndTheMarathons() {
        assertEquals(
            listOf("5mi", "10mi", "HM", "M"),
            DistancePreset.forUnit(DistanceUnit.Miles).map { it.text },
        )
    }

    @Test
    fun aPresetKeepsTheTimeWhilePaceIsComputed() {
        val state = PaceCalculatorState()

        state.selectPreset(DistancePreset.HalfMarathon)

        assertEquals("21.10 km", state.displayedDistance)
        assertEquals("4h 13m 12s", state.displayedTime)
        assertEquals("12:00 min/km", state.displayedPace)
    }

    @Test
    fun aPresetKeepsThePaceWhileTimeIsComputed() {
        val state = PaceCalculatorState()
        state.selectMetric(Metric.Time)

        state.selectPreset(DistancePreset.TenK)

        assertEquals("10.00 km", state.displayedDistance)
        assertEquals("6:00 min/km", state.displayedPace)
        assertEquals("1h 00m 00s", state.displayedTime)
    }

    @Test
    fun aMilePresetLandsOnWholeMiles() {
        val state = PaceCalculatorState()
        state.selectUnit(DistanceUnit.Miles)
        state.selectMetric(Metric.Time)

        state.selectPreset(DistancePreset.TenMiles)

        assertEquals("10.00 mi", state.displayedDistance)
        assertEquals("9:39 min/mi", state.displayedPace)
        assertEquals("1h 36m 34s", state.displayedTime)
    }

    @Test
    fun theHalfMarathonIsTheSameRaceInMiles() {
        val state = PaceCalculatorState()
        state.selectUnit(DistanceUnit.Miles)

        state.selectPreset(DistancePreset.HalfMarathon)

        assertEquals("13.11 mi", state.displayedDistance)
    }

    @Test
    fun switchingUnitsAfterAPresetLeavesTheDistanceAlone() {
        val state = PaceCalculatorState()
        state.selectPreset(DistancePreset.FiveK)

        state.selectUnit(DistanceUnit.Miles)
        assertEquals("3.11 mi", state.displayedDistance)

        state.selectUnit(DistanceUnit.Kilometers)
        assertEquals("5.00 km", state.displayedDistance)
    }

    @Test
    fun aPresetDoesNothingWhileDistanceIsComputed() {
        val state = PaceCalculatorState()
        state.selectMetric(Metric.Distance)

        state.selectPreset(DistancePreset.FiveK)

        assertEquals("42.20 km", state.displayedDistance)
        assertEquals("6:00 min/km", state.displayedPace)
        assertEquals("4h 13m 12s", state.displayedTime)
    }
}
