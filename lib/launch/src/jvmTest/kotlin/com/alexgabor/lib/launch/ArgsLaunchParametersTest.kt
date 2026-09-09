package com.alexgabor.lib.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArgsLaunchParametersTest {

    @Test
    fun readsBothSpellingsOfAnArgument() {
        val parameters = arrayOf("--screen=settings", "--distance", "10").launchParameters()

        assertEquals("settings", parameters["screen"])
        assertEquals("10", parameters["distance"])
    }

    @Test
    fun aFlagWithNothingAfterItIsPresent() {
        val parameters = arrayOf("--verbose", "--screen", "settings").launchParameters()

        assertEquals(true, parameters.boolean("verbose"))
        assertEquals("settings", parameters["screen"])
    }

    @Test
    fun ignoresBareArgumentsAndEmptyFlags() {
        val parameters = arrayOf("run", "--", "--distance=10", "stray").launchParameters()

        assertEquals(mapOf("distance" to "10"), parameters.asMap())
    }

    @Test
    fun noArgumentsGivesTheEmptyParameters() {
        assertTrue(emptyArray<String>().launchParameters().isEmpty)
    }
}
