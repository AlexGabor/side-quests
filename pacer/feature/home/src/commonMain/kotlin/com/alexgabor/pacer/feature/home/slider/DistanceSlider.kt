package com.alexgabor.pacer.feature.home.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.components.track.Track
import com.alexgabor.design.riso.components.track.TrackAlignment
import com.alexgabor.design.riso.components.track.TrackState
import com.alexgabor.pacer.feature.home.twoDigits
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun rememberDistanceSliderState(): DistanceSliderState = remember { DistanceSliderState() }

/**
 * The two rulers that show a distance, and the translation between them and a plain number.
 *
 * The state holds no distance of its own — [PaceCalculatorState] does. This only knows how to put a
 * number onto the rulers and how to read one back off them.
 */
class DistanceSliderState(
    internal val wholeTrackState: TrackState<Int> = TrackState((0..MaxWhole).toList(), subdivisions = 1),
    internal val fractionTrackState: TrackState<Int> = TrackState((0..100 step 5).toList(), subdivisions = 5),
) {
    internal val tracks: List<TrackState<Int>> get() = listOf(wholeTrackState, fractionTrackState)

    val isUserScrolling: Boolean
        get() = wholeTrackState.isUserScrolling || fractionTrackState.isUserScrolling

    /**
     * What the two rulers currently read, in whatever unit the caller is showing. The fraction
     * ruler's last line is 100, a whole unit, which carries — hence the divide rather than a mask.
     */
    val value: Double
        get() = (wholeTrackState.tick * 100 + fractionTrackState.tick) / 100.0

    suspend fun moveTo(value: Double, animate: Boolean): Unit = coroutineScope {
        val hundredths = hundredths(value)
        launch { wholeTrackState.moveToTick(hundredths / 100, animate) }
        launch { fractionTrackState.moveToTick(hundredths % 100, animate) }
    }

    companion object {
        const val MaxWhole = 800

        /** The furthest hundredth the two rulers can jointly show. */
        const val MaxTicks = MaxWhole * 100 + 99

        /** This distance on the rulers' grid, in hundredths, and *not* yet clamped to it. */
        fun ticks(value: Double): Int =
            if (value.isNaN() || !value.isFinite()) 0 else (value * 100).roundToInt()

        /**
         * Where the rulers rest for this distance: the nearest hundredth, parked at an end when the
         * distance is past it. The card is finer — see [PaceCalculatorState.displayedDistance] — so
         * the two can differ in the last digits, but the ruler is always the card rounded.
         */
        fun hundredths(value: Double): Int = ticks(value).coerceIn(0, MaxTicks)
    }
}

@Composable
internal fun DistanceSlider(
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    state: DistanceSliderState = rememberDistanceSliderState(),
) {
    Column(modifier) {
        Track(
            state = state.wholeTrackState,
            itemSize = 100.dp,
            showGuidelineDot = true,
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, _ ->
                Body(item.toString())
            }
        )
        Track(
            state = state.fractionTrackState,
            itemSize = 100.dp,
            trackAlignment = TrackAlignment.Top,
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, subdivision ->
                Body((item + subdivision).twoDigits())
            }
        )
    }
}

@Preview
@Composable
private fun DistanceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val distanceState = rememberDistanceSliderState()
        LaunchedEffect(distanceState) { distanceState.moveTo(42.20, animate = false) }
        Body("Distance ${distanceState.value}")
        DistanceSlider(
            state = distanceState
        )
    }
}
