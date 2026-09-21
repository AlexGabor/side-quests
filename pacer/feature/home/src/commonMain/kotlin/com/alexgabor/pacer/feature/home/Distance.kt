package com.alexgabor.pacer.feature.home

import com.alexgabor.design.riso.components.ButtonGroupItem
import kotlin.jvm.JvmInline
import kotlin.math.roundToInt
import kotlin.math.roundToLong
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

/**
 * This duration as whole hundredths of a second, rounded — the grain of the cards, finer than the
 * rulers'. A [Long] because the cards aren't clamped to a ruler, so a computed time can be as large
 * as it likes. Guards non-finite values the same way [roundedSeconds] does.
 */
internal fun Duration.roundedHundredths(): Long = when {
    !isFinite() -> if (this > Duration.ZERO) Long.MAX_VALUE else 0L
    else -> (inWholeMilliseconds / 10.0).roundToLong()
}

/**
 * This distance to the ten-thousandth — fine enough for the exact half marathon, and the grain of
 * both the cards and the link — without trailing zeros: `21.0975` as `"21.0975"`, `42.2` as
 * `"42.2"`, `10.0` as `"10"`.
 */
internal fun Double.tenThousandthsText(): String {
    val units = if (isFinite()) (this * 10_000).roundToLong() else 0L
    return "${units / 10_000}${fractionText(units % 10_000, digits = 4)}"
}

/**
 * [units] of a [digits]-place fraction as the text after a whole number: 27 hundredths as `".27"`,
 * 50 as `".5"`, and none at all as nothing, point included. Trailing zeros say nothing, so a run
 * that sits on the ruler's lines reads exactly as it did before the cards were finer than them.
 */
internal fun fractionText(units: Long, digits: Int): String {
    val text = units.toString().padStart(digits, '0').trimEnd('0')
    return if (text.isEmpty()) "" else ".$text"
}
