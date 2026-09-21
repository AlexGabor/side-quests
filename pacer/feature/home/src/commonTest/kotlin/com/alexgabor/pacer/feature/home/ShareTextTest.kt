package com.alexgabor.pacer.feature.home

import com.alexgabor.lib.launch.LaunchParameters
import com.alexgabor.pacer.feature.home.slider.DistanceSliderState
import com.alexgabor.pacer.feature.home.slider.PaceSliderState
import com.alexgabor.pacer.feature.home.slider.TimeSliderState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ShareTextTest {

    @Test
    fun theDefaultRunIsSharedAsItsCardsAndALink() {
        assertEquals(
            """
            Time = 4h 13m 12s
            Distance = 42.2 km
            Pace = 6:00 min/km
            https://pacer.alexgabor.com/?distance=42.2&pace=6:00&time=4:13:12&metric=pace&unit=kilometers
            """.trimIndent(),
            PaceCalculatorState().shareText,
        )
    }

    @Test
    fun aRunInMilesIsSharedInMiles() {
        val state = PaceCalculatorState()
        state.selectUnit(DistanceUnit.Miles)

        val lines = state.shareText.lines()

        assertEquals("Distance = 26.2219 mi", lines[1])
        assertEquals("Pace = 9:39.36 min/mi", lines[2])
        assertTrue(lines[3].endsWith("unit=miles"), lines[3])
    }

    @Test
    fun aValuePastItsRulerIsSharedAsTheCardShowsIt() {
        val state = PaceCalculatorState(
            distance = Distance(1000.0),
            pace = 60.minutes,
            time = 1000.hours,
            selectedMetric = Metric.Time,
        )

        assertEquals(state.timeTitle, state.shareText.lines()[0])
        assertEquals("Time = 1000h 00m 00s", state.timeTitle)
    }

    @Test
    fun theSharedLinkOpensOnTheSameRun() {
        val shared = PaceCalculatorState()

        assertEquals(shared.shareText, opened(shared).shareText)
    }

    /**
     * The link carries values to the grain of the cards, so the computed one is worked out again
     * from rounded inputs: here the half marathon is 13.109378 miles but travels as 13.1094, and
     * the time it gives is a hundredth of a second or so off. The inputs as shown, the metric and the unit are what have to
     * survive.
     */
    @Test
    fun theSharedLinkKeepsTheInputsAsShown() {
        val shared = PaceCalculatorState()
        shared.selectUnit(DistanceUnit.Miles)
        shared.selectMetric(Metric.Time)
        shared.selectPreset(DistancePreset.HalfMarathon)

        val opened = opened(shared)

        assertEquals(shared.distanceTitle, opened.distanceTitle)
        assertEquals(shared.paceTitle, opened.paceTitle)
        assertEquals(Metric.Time, opened.selectedMetric)
        assertEquals(DistanceUnit.Miles, opened.selectedUnit)
    }

    /** Links written before the cards were finer than the rulers still open on the same run. */
    @Test
    fun aLinkToTheGrainOfTheRulersStillOpensTheSameRun() {
        val opened = opened(
            "https://pacer.alexgabor.com/?distance=42.20&pace=6:00&time=4:13:12&metric=pace&unit=kilometers",
        )

        assertEquals(PaceCalculatorState().shareText, opened.shareText)
    }

    @Test
    fun aRunBetweenTheRulerLinesIsSharedAndOpenedPrecisely() {
        val shared = PaceCalculatorState(
            distance = Distance(42.195),
            time = 4.hours,
            selectedMetric = Metric.Pace,
        ).apply { selectPreset(DistancePreset.Marathon) }

        assertEquals("Pace = 5:41.27 min/km", shared.paceTitle)
        assertEquals(shared.shareText, opened(shared).shareText)
    }

    /** The run the shared link opens on, read the way a deep link or the web page reads it. */
    private fun opened(shared: PaceCalculatorState): PaceCalculatorState =
        opened(shared.shareText.lines().last())

    private fun opened(url: String): PaceCalculatorState {
        val link = LaunchParameters.ofUrl(url)
        return PaceCalculatorState.launched(
            DistanceSliderState(),
            PaceSliderState(),
            TimeSliderState(),
            PacerLaunchArgs(
                distance = link.double("distance"),
                pace = link.duration("pace"),
                time = link.duration("time"),
                metric = link.enum<Metric>("metric"),
                unit = link.enum<DistanceUnit>("unit"),
            ),
        )
    }
}
