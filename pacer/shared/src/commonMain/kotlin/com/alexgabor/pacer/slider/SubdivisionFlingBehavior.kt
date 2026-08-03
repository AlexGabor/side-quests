package com.alexgabor.pacer.slider

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun rememberSubdivisionFlingBehavior(
    lazyListState: LazyListState,
    itemSizePx: Float,
    subdivision: Int,
    decayAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
): FlingBehavior {
    return remember(lazyListState, itemSizePx, subdivision, decayAnimationSpec) {
        SubdivisionFlingBehavior(lazyListState, itemSizePx, subdivision, decayAnimationSpec)
    }
}

private class SubdivisionFlingBehavior(
    private val lazyListState: LazyListState,
    private val itemSizePx: Float,
    subdivision: Int,
    private val decayAnimationSpec: DecayAnimationSpec<Float>,
) : FlingBehavior {
    private val subdivisionSize: Float = itemSizePx / subdivision

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) > 800) {
            decayFling(initialVelocity)
        }
        return snapFling()
    }

    private suspend fun ScrollScope.decayFling(initialVelocity: Float): Float {
        var velocityLeft = initialVelocity
        var lastValue = 0f
        val animationState = AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
        animationState.animateDecay(decayAnimationSpec) {
            val delta = value - lastValue
            scrollBy(delta)
            lastValue = value
            velocityLeft = this.velocity
        }
        return velocityLeft
    }

    private suspend fun ScrollScope.snapFling(): Float {
        val currentIndex = lazyListState.firstVisibleItemIndex
        val currentOffset = lazyListState.firstVisibleItemScrollOffset
        val currentPx = currentIndex * itemSizePx + currentOffset

        val targetPx = ((currentPx / subdivisionSize).roundToInt() * subdivisionSize)
        val delta = targetPx - currentPx

        if (delta != 0f) {
            var previousValue = 0f
            animate(0f, delta) { currentValue, _ ->
                val frameDelta = currentValue - previousValue
                val consumed = scrollBy(frameDelta)
                previousValue += consumed
            }
        }

        return 0f
    }
}
