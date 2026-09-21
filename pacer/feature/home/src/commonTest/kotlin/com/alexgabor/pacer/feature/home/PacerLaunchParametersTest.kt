package com.alexgabor.pacer.feature.home

import com.alexgabor.lib.launch.LaunchParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How a run is written down, for a url or anything else that takes a launch. */
class PacerLaunchParametersTest {

    @Test
    fun aRunIsWrittenOutInFull() {
        val args = PacerLaunchArgs(
            distance = 21.1,
            pace = 5.minutes,
            time = 1.hours + 45.minutes + 30.seconds,
            metric = Metric.Time,
            unit = DistanceUnit.Kilometers,
        )

        assertEquals(
            "distance=21.1&pace=5:00&time=1:45:30&metric=time&unit=kilometers",
            args.toLaunchParameters().toQueryString(),
        )
    }

    @Test
    fun whatIsMissingOrUnwritableIsLeftOut() {
        assertTrue(PacerLaunchArgs.None.toLaunchParameters().isEmpty)
        assertEquals(
            "pace=5:00",
            PacerLaunchArgs(
                distance = Double.NaN,
                pace = 5.minutes,
                time = Duration.INFINITE,
            ).toLaunchParameters().toQueryString(),
        )
    }

    @Test
    fun aRunIsWrittenToTheGrainOfTheCards() {
        val args = PacerLaunchArgs(
            distance = 13.109898,
            pace = 5.minutes + 0.44.seconds,
            unit = DistanceUnit.Miles,
        )

        val written = args.toLaunchParameters()

        assertEquals("13.1099", written["distance"])
        assertEquals("5:00.44", written["pace"])
    }

    @Test
    fun aWrittenRunReadsBackAsTheSameNumbers() {
        val written = PacerLaunchArgs(
            distance = 42.2,
            pace = 6.minutes,
            time = 4.hours + 13.minutes + 12.seconds,
        ).toLaunchParameters()

        val read = LaunchParameters.ofQueryString(written.toQueryString())

        assertEquals(42.2, read.double("distance"))
        assertEquals(6.minutes, read.duration("pace"))
        assertEquals(4.hours + 13.minutes + 12.seconds, read.duration("time"))
    }
}
