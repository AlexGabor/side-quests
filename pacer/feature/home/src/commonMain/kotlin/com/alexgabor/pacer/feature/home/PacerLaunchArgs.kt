package com.alexgabor.pacer.feature.home

import com.alexgabor.lib.launch.LaunchParameters
import kotlin.math.roundToLong
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
 * `distance=21.10&pace=5:00&time=1:45:30&metric=time&unit=kilometers`. Distance is to the
 * hundredth and durations to the second, which is the grain of the rulers; it also rounds away
 * the float noise a run picks up by being held in kilometres and shown in miles.
 */
fun PacerLaunchArgs.toLaunchParameters(): LaunchParameters {
    val values = buildMap {
        distance?.takeIf { it.isFinite() && it >= 0.0 }?.let { put("distance", it.hundredths()) }
        pace?.let(LaunchParameters::formatDuration)?.let { put("pace", it) }
        time?.let(LaunchParameters::formatDuration)?.let { put("time", it) }
        metric?.let { put("metric", it.name.lowercase()) }
        unit?.let { put("unit", it.name.lowercase()) }
    }
    return if (values.isEmpty()) LaunchParameters.Empty else LaunchParameters(values)
}

/** `21.1` as `"21.10"` — always two places, as the distance card shows it. */
private fun Double.hundredths(): String {
    val hundredths = (this * 100).roundToLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
