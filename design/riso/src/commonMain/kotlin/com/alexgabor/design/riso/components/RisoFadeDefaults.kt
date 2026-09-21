package com.alexgabor.design.riso.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The fades a crossfade runs, and the numbers they are built from.
 *
 * Both sides ease fast–slow–fast: quick off the mark, slowest as they pass through half ink, quick
 * again to the end. The print in between — two pieces of artwork, each at about half its dots, with
 * the stock showing through — stays on the sheet long enough to be seen, and the ink never stops
 * moving while it does, which a held value would and which reads as a stall. The incoming side
 * starts while the outgoing one is still passing through the middle, so the two overlap:
 *
 * ```
 *        0 ms   88     100    175    188    275
 * out    1      0.5           0
 * in                   0             0.5    1
 * ```
 *
 * Both are tweens, which start from wherever the value is, so a fade that is turned round part-way
 * carries on from the ink it had reached instead of jumping back to full or to nothing first.
 */
object RisoFadeDefaults {

    /** How long each side takes to run from one end to the other. */
    const val FadeMillis: Int = 175

    /** When the incoming side starts, measured from the start of the outgoing one. */
    const val InDelayMillis: Int = 100

    /** The whole crossfade, which is when the incoming side reaches full ink. */
    const val CrossfadeMillis: Int = InDelayMillis + FadeMillis

    /** Fast, slow through the middle, fast again — lingering at half ink without stopping there. */
    val LingerEasing: Easing = CubicBezierEasing(0.15f, 0.6f, 0.85f, 0.4f)

    /** Full ink down to none, lingering around half. */
    val crossfadeOut: FiniteAnimationSpec<Float> =
        tween(durationMillis = FadeMillis, easing = LingerEasing)

    /** No ink up to full, starting [InDelayMillis] after [crossfadeOut] and lingering around half. */
    val crossfadeIn: FiniteAnimationSpec<Float> =
        tween(durationMillis = FadeMillis, delayMillis = InDelayMillis, easing = LingerEasing)
}
