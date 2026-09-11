package com.alexgabor.pacer

import com.alexgabor.design.navigation.DeepLink
import com.alexgabor.lib.launch.LaunchParameters
import com.alexgabor.pacer.feature.home.DistanceUnit
import com.alexgabor.pacer.feature.home.Metric


/**
 * Where [launch] asks the app to open.
 *
 * A named screen comes back with its parent underneath it, so that a deep link into Settings still
 * has somewhere to go back to. Anything unrecognized comes back empty, and each display falls
 * through to its own default.
 */
internal fun pacerDeepLink(launch: LaunchParameters): DeepLink {
    if (launch.isEmpty) return DeepLink.Empty

    val pacer = RootDestination.Pacer(
        distance = launch.double("distance"),
        paceMillis = launch.duration("pace")?.inWholeMilliseconds,
        timeMillis = launch.duration("time")?.inWholeMilliseconds,
        metric = launch.enum<Metric>("metric")?.name,
        unit = launch.enum<DistanceUnit>("unit")?.name,
    )

    return when (launch["screen"]?.lowercase()) {
        "settings" -> DeepLink(listOf(pacer, RootDestination.Settings))
        "pacer" -> DeepLink(listOf(pacer))
        // An unnamed screen still carries its arguments, but only if there are any: an empty key
        // is what the display would have built for itself.
        else -> if (pacer == RootDestination.Pacer()) DeepLink.Empty else DeepLink(listOf(pacer))
    }
}
