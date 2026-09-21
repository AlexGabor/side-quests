package com.alexgabor.design.riso.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.alexgabor.design.riso.components.RisoFadeDefaults.CrossfadeMillis
import com.alexgabor.design.riso.components.RisoFadeDefaults.FadeMillis
import com.alexgabor.design.riso.components.RisoFadeDefaults.InDelayMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RisoFadeDefaultsTest {

    private val out = sampler(RisoFadeDefaults.crossfadeOut, from = 1f, to = 0f)
    private val `in` = sampler(RisoFadeDefaults.crossfadeIn, from = 0f, to = 1f)

    @Test
    fun theWayOutPassesHalfInkHalfWayAndEndsOnNone() {
        assertEquals(1f, out(0f), TOLERANCE)
        assertEquals(0.5f, out(FadeMillis / 2f), TOLERANCE)
        assertEquals(0f, out(FadeMillis.toFloat()), TOLERANCE)
    }

    @Test
    fun theWayInStartsWhileTheWayOutIsStillGoing() {
        assertTrue(InDelayMillis < FadeMillis, "the two sides do not overlap")
        assertEquals(0f, `in`(0f), TOLERANCE)
        assertEquals(0f, `in`(InDelayMillis.toFloat()), TOLERANCE)
        assertEquals(0.5f, `in`(InDelayMillis + FadeMillis / 2f), TOLERANCE)
        assertEquals(1f, `in`(CrossfadeMillis.toFloat()), TOLERANCE)
    }

    @Test
    fun itLingersAroundHalfInkWithoutStopping() {
        // Slowest in the middle: the tenth of the way around half ink covers less than the first.
        val tenth = FadeMillis / 10f
        val atStart = out(0f) - out(tenth)
        val inMiddle = out(FadeMillis / 2f - tenth / 2) - out(FadeMillis / 2f + tenth / 2)
        assertTrue(inMiddle < atStart, "no linger: $inMiddle in the middle, $atStart at the start")
        // But never still: every step of the way out lays down less ink than the step before.
        val step = FadeMillis / 40f
        (1..40).forEach { i ->
            assertTrue(out(i * step) < out((i - 1) * step), "the ink stopped moving at ${i * step} ms")
        }
    }

    @Test
    fun bothSidesAreOnTheSheetInTheOverlap() {
        val overlap = (InDelayMillis + FadeMillis) / 2f
        assertTrue(out(overlap) in 0.01f..0.99f, "out was ${out(overlap)} at $overlap ms")
        assertTrue(`in`(overlap) in 0.01f..0.99f, "in was ${`in`(overlap)} at $overlap ms")
    }

    @Test
    fun aTurnedRoundFadeStartsFromWhereItWas() {
        val fromAThird = sampler(RisoFadeDefaults.crossfadeOut, from = 0.3f, to = 0f)
        assertEquals(0.3f, fromAThird(0f), TOLERANCE)
    }

    private fun sampler(spec: FiniteAnimationSpec<Float>, from: Float, to: Float): (Float) -> Float {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, from, to)
        return { millis -> animation.getValueFromNanos((millis * 1_000_000L).toLong()) }
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
