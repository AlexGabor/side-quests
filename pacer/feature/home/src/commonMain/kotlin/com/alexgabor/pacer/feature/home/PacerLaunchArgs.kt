package com.alexgabor.pacer.feature.home

import com.alexgabor.lib.launch.LaunchParameters
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

/** Where Pacer runs on the web; a link here with a run's launch parameters opens on that run. */
const val PacerWebUrl = "https://pacer.alexgabor.com/"

/**
 * The launch that opens on this run — what a deep link into it would have said.
 *
 * Everything is spelled out, in a fixed order, as a person would read it:
 * `distance=21.1&pace=5:00&time=1:45:30&metric=time&unit=kilometers`. Distance is to the
 * ten-thousandth and durations to the hundredth of a second, trailing zeros dropped, which is how
 * the cards read, so the link carries what the user was shown; it also rounds away the float noise a run picks up by
 * being held in kilometres and shown in miles.
 */
fun PacerLaunchArgs.toLaunchParameters(): LaunchParameters {
    val values = buildMap {
        distance?.takeIf { it.isFinite() && it >= 0.0 }?.let { put("distance", it.tenThousandthsText()) }
        pace?.let { LaunchParameters.formatDuration(it, fractionDigits = 2) }?.let { put("pace", it) }
        time?.let { LaunchParameters.formatDuration(it, fractionDigits = 2) }?.let { put("time", it) }
        metric?.let { put("metric", it.name.lowercase()) }
        unit?.let { put("unit", it.name.lowercase()) }
    }
    return if (values.isEmpty()) LaunchParameters.Empty else LaunchParameters(values)
}

