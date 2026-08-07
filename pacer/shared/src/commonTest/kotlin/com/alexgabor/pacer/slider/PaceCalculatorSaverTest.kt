package com.alexgabor.pacer.slider

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

/** What survives a rotation, and what it survives as. */
class PaceCalculatorSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun `every metric comes back as itself`() {
        for (metric in Metric.entries) {
            assertEquals(metric, MetricSaver.roundTrip(metric))
        }
    }

    @Test
    fun `every unit comes back as itself`() {
        for (unit in DistanceUnit.entries) {
            assertEquals(unit, DistanceUnitSaver.roundTrip(unit))
        }
    }

    /**
     * Names, not ordinals: a bundle written by an older build can be restored by a newer one, and
     * reordering the entries must not quietly turn a saved Miles into Kilometers.
     */
    @Test
    fun `selections are saved by name`() {
        assertEquals("Distance", MetricSaver.saved(Metric.Distance))
        assertEquals("Miles", DistanceUnitSaver.saved(DistanceUnit.Miles))
    }

    private fun <T> Saver<T, String>.saved(value: T): String? = with(scope) { save(value) }

    private fun <T> Saver<T, String>.roundTrip(value: T): T? = restore(saved(value)!!)
}
