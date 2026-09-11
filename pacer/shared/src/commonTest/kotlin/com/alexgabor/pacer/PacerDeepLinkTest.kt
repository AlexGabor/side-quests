package com.alexgabor.pacer

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.serialization.json.Json
import com.alexgabor.pacer.feature.home.DistanceUnit
import com.alexgabor.pacer.feature.home.Metric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PacerDeepLinkTest {

    private fun deepLinkOf(query: String) =
        pacerDeepLink(LaunchParameters.ofQueryString(query))

    @Test
    fun nothingLaunchedWithGivesAnEmptyDeepLink() {
        assertTrue(pacerDeepLink(LaunchParameters.Empty).isEmpty)
        assertTrue(deepLinkOf("theme=dark").isEmpty)
    }

    @Test
    fun aNamedScreenComesBackWithItsParentUnderneath() {
        assertEquals(
            listOf(RootDestination.Pacer(), RootDestination.Settings),
            deepLinkOf("screen=settings").parts,
        )
    }

    @Test
    fun theScreenNameIsReadWithoutRegardToCase() {
        assertEquals(
            listOf(RootDestination.Pacer(), RootDestination.Settings),
            deepLinkOf("screen=Settings").parts,
        )
    }

    @Test
    fun argumentsLandOnThePacerKey() {
        val parts = deepLinkOf("distance=21.1&pace=5:00&time=1:45:30&metric=Time&unit=Miles").parts

        assertEquals(
            listOf(
                RootDestination.Pacer(
                    distance = 21.1,
                    paceMillis = 5.minutes.inWholeMilliseconds,
                    timeMillis = (1.hours + 45.minutes + 30.seconds).inWholeMilliseconds,
                    metric = Metric.Time.name,
                    unit = DistanceUnit.Miles.name,
                ),
            ),
            parts,
        )
    }

    @Test
    fun argumentsSurviveAlongsideANamedScreen() {
        val parts = deepLinkOf("screen=settings&distance=10").parts

        assertEquals(
            listOf(RootDestination.Pacer(distance = 10.0), RootDestination.Settings),
            parts,
        )
    }

    @Test
    fun anUnreadableArgumentIsDroppedRatherThanFailing() {
        assertTrue(deepLinkOf("distance=banana&metric=Furlongs").isEmpty)
    }

    @Test
    fun theKeySerializesAndComesBackWhole() {
        val key: RootDestination = RootDestination.Pacer(
            distance = 21.1,
            paceMillis = 300_000,
            metric = Metric.Time.name,
            unit = DistanceUnit.Miles.name,
        )

        val encoded = Json.encodeToString(RootDestination.serializer(), key)

        assertEquals(key, Json.decodeFromString(RootDestination.serializer(), encoded))
    }

    @Test
    fun aKeyWrittenBeforeAFieldExistedStillDecodes() {
        val key: RootDestination = RootDestination.Pacer(distance = 10.0)

        // Defaults are not written, so this is also what a key from a build that predates the other
        // fields looks like — decoding it has to fall back rather than throw.
        val encoded = Json.encodeToString(RootDestination.serializer(), key)
        assertFalse("metric" in encoded, "expected defaults to be omitted, got $encoded")

        assertEquals(key, Json.decodeFromString(RootDestination.serializer(), encoded))
    }
}
