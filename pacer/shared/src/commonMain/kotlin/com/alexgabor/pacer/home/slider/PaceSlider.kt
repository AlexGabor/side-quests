package com.alexgabor.pacer.home.slider

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
import com.alexgabor.design.riso.components.track.TrackSate
import com.alexgabor.pacer.home.roundedSeconds
import com.alexgabor.pacer.home.twoDigits
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun rememberPaceSliderState(): PaceSliderState = remember { PaceSliderState() }

/**
 * The two rulers that show a pace — minutes and seconds per distance unit — and the translation
 * between them and a [Duration].
 *
 * The minute ruler runs to 59 rather than the 20 it used to: in miles a walking pace is already
 * 20:00/mi, and anything long and slow (a marathon in a day is 34:00/km) fell off the end and was
 * silently dropped.
 */
class PaceSliderState(
    internal val minuteTrackState: TrackSate<Int> = TrackSate((0..59).toList(), subdivisions = 1),
    internal val secondTrackState: TrackSate<Int> = TrackSate((0..60 step 10).toList(), subdivisions = 10),
) {
    internal val tracks: List<TrackSate<Int>> get() = listOf(minuteTrackState, secondTrackState)

    val isUserScrolling: Boolean
        get() = minuteTrackState.isUserScrolling || secondTrackState.isUserScrolling

    /** What the two rulers currently read, per whichever unit the caller is showing. */
    val value: Duration
        get() = (minuteTrackState.tick * 60 + secondTrackState.tick).seconds

    suspend fun moveTo(value: Duration, animate: Boolean): Unit = coroutineScope {
        val seconds = seconds(value)
        launch { minuteTrackState.moveToTick(seconds / 60, animate) }
        launch { secondTrackState.moveToTick(seconds % 60, animate) }
    }

    companion object {
        /** The furthest second the two rulers can jointly show. */
        const val MaxTicks = 59 * 60 + 59

        /**
         * This pace on the rulers' grid, in seconds, and *not* clamped to it — a caller comparing
         * against [MaxTicks] is how the readout knows to say `>` instead of `=`.
         */
        fun ticks(value: Duration): Int = value.roundedSeconds()

        /** The single quantiser, shared by the rulers and the readout so they cannot disagree. */
        fun seconds(value: Duration): Int = ticks(value).coerceIn(0, MaxTicks)
    }
}

@Composable
fun PaceSlider(
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    state: PaceSliderState = rememberPaceSliderState(),
) {
    Column(modifier) {
        Track(
            state = state.minuteTrackState,
            itemSize = 100.dp,
            showGuidelineDot = true,
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, _ ->
                Body(text = item.toString())
            }
        )
        Track(
            state = state.secondTrackState,
            itemSize = 200.dp,
            trackAlignment = TrackAlignment.Top,
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, subdivision ->
                Body(text = (item + subdivision).twoDigits())
            }
        )
    }
}


@Preview
@Composable
private fun PaceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val paceState = rememberPaceSliderState()
        LaunchedEffect(paceState) { paceState.moveTo(6.minutes, animate = false) }
        Body(text = "Pace ${paceState.value}")
        PaceSlider(
            state = paceState
        )
    }
}
