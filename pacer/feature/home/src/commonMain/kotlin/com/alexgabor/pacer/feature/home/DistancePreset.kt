package com.alexgabor.pacer.feature.home

/**
 * A race distance one tap away.
 *
 * The kilometre races are rounded to the ruler's hundredths — 21.10 rather than 21.0975 — so a
 * preset lands on a line of the ruler and reads on the card as exactly what was tapped. The mile
 * races are held exactly, and 5 and 10 miles land on their own lines when miles are shown.
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
    HalfMarathon("HM", Distance(21.10), DistanceUnit.entries.toSet()),
    Marathon("M", Distance(42.20), DistanceUnit.entries.toSet());

    companion object {
        /** The presets offered while [unit] is on screen, in the order they are shown. */
        fun forUnit(unit: DistanceUnit): List<DistancePreset> = entries.filter { unit in it.units }
    }
}
