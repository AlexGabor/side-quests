package com.alexgabor.lib.appstateurl

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Against the browser's own history, which the test page really does own: every test puts the url
 * back the way it found it afterwards.
 */
class BrowserAppUrlTest {

    private val startingUrl = locationHref()

    @AfterTest
    fun restoreTheUrl() = replaceUrl(startingUrl)

    private fun urlWith(query: String): BrowserAppUrl {
        replaceUrl("${locationPathname()}?$query")
        return BrowserAppUrl()
    }

    @Test
    fun itStartsOnWhatTheUrlAlreadySays() {
        val appUrl = urlWith("distance=10&pace=5:00")

        assertEquals(
            mapOf("distance" to "10", "pace" to "5:00"),
            appUrl.parameters.value.asMap(),
        )
    }

    @Test
    fun replacingWritesOverTheKeysGivenAndLeavesTheRest() {
        val appUrl = urlWith("distance=10&pace=5:00")

        appUrl.replace(LaunchParameters(mapOf("pace" to "4:30")))

        assertEquals("?distance=10&pace=4:30", locationSearch())
        assertEquals(
            mapOf("distance" to "10", "pace" to "4:30"),
            appUrl.parameters.value.asMap(),
        )
    }

    @Test
    fun replacingLeavesTheHistoryAsLongAsItWas() {
        val appUrl = urlWith("distance=10")
        val entries = historyLength()

        appUrl.replace(LaunchParameters(mapOf("distance" to "11")))
        appUrl.replace(LaunchParameters(mapOf("distance" to "12")))

        assertEquals(entries, historyLength())
    }

    @Test
    fun pushingAddsSomewhereToComeBackTo() {
        val appUrl = urlWith("distance=10")
        val entries = historyLength()

        appUrl.push(LaunchParameters(mapOf("screen" to "settings")))

        assertEquals(entries + 1, historyLength())
        assertEquals("?distance=10&screen=settings", locationSearch())
    }

    @Test
    fun thereIsNowhereToGoBackToBeforeAnythingIsPushed() {
        val appUrl = urlWith("distance=10")

        // Whatever is behind belongs to wherever the user came from, and going there would leave
        // the page altogether.
        assertFalse(appUrl.back())
    }

    @Test
    fun goingBackReturnsToTheEntryBeforeThePush() = runTest {
        val appUrl = urlWith("distance=10")
        appUrl.push(LaunchParameters(mapOf("screen" to "settings")))

        assertTrue(appUrl.back())

        // The browser tells the page it has moved, in its own time — the same event as the user
        // pressing Back.
        val returned = appUrl.parameters.first { it["screen"] == null }
        assertEquals(mapOf("distance" to "10"), returned.asMap())
        assertEquals("?distance=10", locationSearch())
        assertFalse(appUrl.back(), "expected to be back at the entry the page was opened on")
    }

    @Test
    fun writingTheSameThingAgainDoesNothing() {
        val appUrl = urlWith("distance=10")
        val entries = historyLength()

        appUrl.push(LaunchParameters(mapOf("distance" to "10")))

        assertEquals(entries, historyLength())
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationHref(): String = js("window.location.href")

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationSearch(): String = js("window.location.search")

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationPathname(): String = js("window.location.pathname")

@OptIn(ExperimentalWasmJsInterop::class)
private fun historyLength(): Int = js("window.history.length")

@OptIn(ExperimentalWasmJsInterop::class)
private fun replaceUrl(url: String): Unit = js("window.history.replaceState(null, '', url)")
