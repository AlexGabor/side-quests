package com.alexgabor.pacer.slider

import com.alexgabor.design.riso.components.ButtonGroupItem
import kotlin.jvm.JvmInline
import kotlin.math.roundToInt
import kotlin.time.Duration

const val KILOMETERS_PER_MILE = 1.609344

enum class DistanceUnit(override val text: String) : ButtonGroupItem {
    Kilometers("km"),
    Miles("mi");

    val paceText: String get() = "min/$text"

    /** How many kilometres one of these is — the only conversion factor anything needs. */
    val kilometers: Double get() = if (this == Kilometers) 1.0 else KILOMETERS_PER_MILE
}

/**
 * A distance, always held in kilometres.
 *
 * Storing one canonical unit is what makes switching between km and miles lossless: the number on
 * the ruler changes, the run being described doesn't, and switching back gives the original figure
 * rather than something that has been rounded through the ruler twice.
 */
@JvmInline
value class Distance(val kilometers: Double) {
    fun inUnit(unit: DistanceUnit): Double = kilometers / unit.kilometers

    companion object {
        fun of(value: Double, unit: DistanceUnit) = Distance(value * unit.kilometers)
    }
}

/**
 * This duration as whole seconds, rounded rather than truncated.
 *
 * Guards its own non-finite cases: a pace computed from a zero distance is [Duration.INFINITE], and
 * letting that reach [Double.roundToInt] would throw rather than produce a number.
 */
internal fun Duration.roundedSeconds(): Int = when {
    !isFinite() -> if (this > Duration.ZERO) Int.MAX_VALUE else 0
    else -> (inWholeMilliseconds / 1000.0).roundToInt()
}
