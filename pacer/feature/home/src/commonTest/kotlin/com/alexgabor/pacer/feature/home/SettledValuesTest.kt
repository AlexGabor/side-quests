package com.alexgabor.pacer.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the calculator reports once it comes to rest — the run a url should be describing.
 *
 * Driven through plain flags rather than real sliders, for the same reason as
 * [com.alexgabor.pacer.feature.home.slider.UserScrollTest]: a gesture needs a laid-out ruler, and
 * what is under test here is only what happens around one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettledValuesTest {

    private val scrolling = mutableStateOf(false)
    private val value = mutableStateOf(0)

    private fun TestScope.collectSettled(): List<Int> {
        val reported = mutableListOf<Int>()
        backgroundScope.launch {
            settledValues({ scrolling.value }, { value.value }).collect { reported += it }
        }
        runCurrent()
        return reported
    }

    private fun set(scrolling: Boolean = this.scrolling.value, value: Int = this.value.value) {
        Snapshot.withMutableSnapshot {
            this.scrolling.value = scrolling
            this.value.value = value
        }
    }

    /**
     * Past the delay, by the clock rather than `advanceUntilIdle`: the collector lives in
     * `backgroundScope`, whose timers `advanceUntilIdle` doesn't wait for.
     */
    private fun TestScope.waitOutTheSettle() {
        runCurrent()
        advanceTimeBy(SettleDelay + 1.milliseconds)
    }

    @Test
    fun theValueItStartsOnIsNotReported() = runTest {
        val reported = collectSettled()

        waitOutTheSettle()

        // That is what the screen was opened with, so there is nothing new to say about it.
        assertEquals(emptyList(), reported)
    }

    @Test
    fun aChangeIsReportedOnceItHasSatStill() = runTest {
        val reported = collectSettled()

        set(value = 5)
        runCurrent()
        advanceTimeBy(SettleDelay - 1.milliseconds)
        assertEquals(emptyList(), reported)

        advanceTimeBy(2.milliseconds)
        assertEquals(listOf(5), reported)
    }

    @Test
    fun nothingIsReportedWhileASliderIsMoving() = runTest {
        val reported = collectSettled()

        set(scrolling = true)
        for (step in 1..3) {
            set(value = step)
            // Longer than the delay: a slow drag still isn't a place the user has stopped at.
            advanceTimeBy(SettleDelay * 2)
        }
        assertEquals(emptyList(), reported)

        set(scrolling = false)
        waitOutTheSettle()
        assertEquals(listOf(3), reported)
    }

    @Test
    fun aBurstOfChangesIsReportedOnce() = runTest {
        val reported = collectSettled()

        for (step in 1..3) {
            set(value = step)
            advanceTimeBy(SettleDelay / 3)
        }
        waitOutTheSettle()

        assertEquals(listOf(3), reported)
    }

    @Test
    fun comingBackToTheSameValueIsNotReportedAgain() = runTest {
        val reported = collectSettled()

        set(value = 5)
        waitOutTheSettle()

        // Out and back within one gesture: the run at rest is the one already reported.
        set(scrolling = true)
        set(value = 6)
        set(value = 5)
        set(scrolling = false)
        waitOutTheSettle()

        assertEquals(listOf(5), reported)
    }

    @Test
    fun theLastValueOfAGestureWinsEvenWhenItLandsJustAfterTheEnd() = runTest {
        val reported = collectSettled()

        set(scrolling = true, value = 4)
        runCurrent()
        // The gesture ends a frame before the last value is written, as it can in a real slider.
        set(scrolling = false)
        runCurrent()
        set(value = 5)
        waitOutTheSettle()

        assertEquals(listOf(5), reported)
    }
}
