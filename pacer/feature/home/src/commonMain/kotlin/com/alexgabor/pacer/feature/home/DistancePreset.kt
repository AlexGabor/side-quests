package com.alexgabor.pacer.feature.home

/**
 * A race distance one tap away.
 *
 * Every race is held at its exact distance — the half marathon is 21.0975 km, not the 21.10 the
 * ruler can show. The ruler rests on the nearest hundredth while the card, which reads to the
 * ten-thousandth, shows exactly the race that was tapped.
 *
 * The half and full marathon are the same race whichever unit is on screen, so they are offered in
 * both rather than once per unit.
 */
enum class DistancePreset(
    val text: String,
    val distance: Distance,
    private val units: Set<DistanceUnit>,
) {
    FiveK("5K", Distance(5.00), setOf(DistanceUnit.Kilometers)),
    TenK("10K", Distance(10.00), setOf(DistanceUnit.Kilometers)),
    FiveMiles("5mi", Distance.of(5.0, DistanceUnit.Miles), setOf(DistanceUnit.Miles)),
    TenMiles("10mi", Distance.of(10.0, DistanceUnit.Miles), setOf(DistanceUnit.Miles)),
    HalfMarathon("HM", Distance(21.0975), DistanceUnit.entries.toSet()),
    Marathon("M", Distance(42.195), DistanceUnit.entries.toSet());

    companion object {
        /** The presets offered while [unit] is on screen, in the order they are shown. */
        fun forUnit(unit: DistanceUnit): List<DistancePreset> = entries.filter { unit in it.units }
    }
}
