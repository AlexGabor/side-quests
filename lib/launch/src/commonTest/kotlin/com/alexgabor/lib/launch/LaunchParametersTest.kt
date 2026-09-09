package com.alexgabor.lib.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LaunchParametersTest {

    private enum class Unit { Kilometers, Miles }

    @Test
    fun parsesABareQueryString() {
        val parameters = LaunchParameters.ofQueryString("distance=10&screen=settings")

        assertEquals("10", parameters["distance"])
        assertEquals("settings", parameters["screen"])
    }

    @Test
    fun parsesASearchAFragmentAndAWholeUrlAlike() {
        val expected = mapOf("distance" to "10")

        assertEquals(expected, LaunchParameters.ofQueryString("?distance=10").asMap())
        assertEquals(expected, LaunchParameters.ofQueryString("#distance=10").asMap())
        assertEquals(
            expected,
            LaunchParameters.ofQueryString("https://example.com/app?distance=10").asMap(),
        )
        assertEquals(
            expected,
            LaunchParameters.ofQueryString("pacer://pacer?distance=10#top").asMap(),
        )
    }

    @Test
    fun decodesPercentEscapesAndPlusAsSpace() {
        val parameters = LaunchParameters.ofQueryString("name=half%20marathon&note=a+b&emoji=%F0%9F%8F%83")

        assertEquals("half marathon", parameters["name"])
        assertEquals("a b", parameters["note"])
        assertEquals("🏃", parameters["emoji"])
    }

    @Test
    fun keepsCharactersThatWereNeverEscaped() {
        // Browsers do not always escape, so raw multi-byte characters have to survive intact
        // alongside escaped ones.
        val parameters = LaunchParameters.ofQueryString("name=caf\u00e9 🏃&other=%F0%9F%8F%83")

        assertEquals("café 🏃", parameters["name"])
        assertEquals("🏃", parameters["other"])
    }

    @Test
    fun leavesAMalformedEscapeAsWritten() {
        assertEquals("100%", LaunchParameters.ofQueryString("effort=100%").asMap()["effort"])
    }

    @Test
    fun emptyAndBlankInputGiveTheEmptyParameters() {
        assertTrue(LaunchParameters.ofQueryString(null).isEmpty)
        assertTrue(LaunchParameters.ofQueryString("").isEmpty)
        assertTrue(LaunchParameters.ofQueryString("   ").isEmpty)
        assertTrue(LaunchParameters.ofQueryString("?").isEmpty)
    }

    @Test
    fun readsNumbersAndAnswersNullForWhatIsNotOne() {
        val parameters = LaunchParameters(mapOf("a" to "42.2", "b" to "banana", "c" to "7"))

        assertEquals(42.2, parameters.double("a"))
        assertNull(parameters.double("b"))
        assertNull(parameters.double("missing"))
        assertEquals(7L, parameters.long("c"))
        assertNull(parameters.long("a"))
    }

    @Test
    fun readsDurationsWrittenTheWayARunnerWritesThem() {
        val parameters = LaunchParameters(
            mapOf(
                "pace" to "5:30",
                "time" to "4:13:12",
                "rest" to "90",
                "long" to "90:00",
                "split" to "1:30.5",
            ),
        )

        assertEquals(5.minutes + 30.seconds, parameters.duration("pace"))
        assertEquals(4.hours + 13.minutes + 12.seconds, parameters.duration("time"))
        assertEquals(90.seconds, parameters.duration("rest"))
        assertEquals(90.minutes, parameters.duration("long"))
        assertEquals(90.5.seconds, parameters.duration("split"))
    }

    @Test
    fun refusesADurationThatIsNotOne() {
        val parameters = LaunchParameters(
            mapOf("a" to "banana", "b" to "1:2:3:4", "c" to "-5:00", "d" to ""),
        )

        assertNull(parameters.duration("a"))
        assertNull(parameters.duration("b"))
        assertNull(parameters.duration("c"))
        assertNull(parameters.duration("d"))
        assertNull(parameters.duration("missing"))
    }

    @Test
    fun readsBooleansCountingAValuelessFlagAsTrue() {
        val parameters = LaunchParameters(
            mapOf("a" to "true", "b" to "0", "c" to "", "d" to "ON", "e" to "maybe"),
        )

        assertEquals(true, parameters.boolean("a"))
        assertEquals(false, parameters.boolean("b"))
        assertEquals(true, parameters.boolean("c"))
        assertEquals(true, parameters.boolean("d"))
        assertNull(parameters.boolean("e"))
        assertNull(parameters.boolean("missing"))
    }

    @Test
    fun readsEnumsByNameIgnoringCase() {
        val parameters = LaunchParameters(mapOf("a" to "miles", "b" to "Furlongs"))

        assertEquals(Unit.Miles, parameters.enum<Unit>("a"))
        assertNull(parameters.enum<Unit>("b"))
        assertNull(parameters.enum<Unit>("missing"))
    }
}
