package com.alexgabor.pacer.home

import kotlin.time.Duration

/**
 * The run a launch parameter asked the calculator to open on.
 *
 * Everything is optional, and [distance] and [pace] are in [unit] rather than in kilometres — this
 * is what the user typed, not what the calculator holds. Turning it into a consistent run is
 * [PaceCalculatorState]'s job, because only it knows which of the three values is the computed one.
 */
data class PacerLaunchArgs(
    val distance: Double? = null,
    val pace: Duration? = null,
    val time: Duration? = null,
    val metric: Metric? = null,
    val unit: DistanceUnit? = null,
) {
    val isEmpty: Boolean
        get() = distance == null && pace == null && time == null && metric == null && unit == null

    companion object {
        val None = PacerLaunchArgs()
    }
}
