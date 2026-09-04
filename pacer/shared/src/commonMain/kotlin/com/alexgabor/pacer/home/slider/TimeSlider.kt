package com.alexgabor.pacer.home.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Composable
fun rememberTimeSliderState(): TimeSliderState = remember { TimeSliderState() }

/**
 * The three rulers that show an elapsed time, and the translation between them and a [Duration].
 */
class TimeSliderState(
    internal val hourTrackState: TrackSate<Int> = TrackSate((0..120).toList(), subdivisions = 1),
    internal val minuteTrackState: TrackSate<Int> = TrackSate((0..59).toList(), subdivisions = 1),
    internal val secondTrackState: TrackSate<Int> = TrackSate((0..60 step 5).toList(), subdivisions = 5),
) {
    internal val tracks: List<TrackSate<Int>>
        get() = listOf(hourTrackState, minuteTrackState, secondTrackState)

    val isUserScrolling: Boolean
        get() = hourTrackState.isUserScrolling ||
            minuteTrackState.isUserScrolling ||
            secondTrackState.isUserScrolling

    /** What the three rulers currently read. Each one's last line carries into the next. */
    val value: Duration
        get() = (hourTrackState.tick * 3600 + minuteTrackState.tick * 60 + secondTrackState.tick)
            .seconds

    suspend fun moveTo(value: Duration, animate: Boolean): Unit = coroutineScope {
        val seconds = seconds(value)
        launch { hourTrackState.moveToTick(seconds / 3600, animate) }
        launch { minuteTrackState.moveToTick((seconds % 3600) / 60, animate) }
        launch { secondTrackState.moveToTick(seconds % 60, animate) }
    }

    companion object {
        /** The furthest second the three rulers can jointly show. */
        const val MaxTicks = 120 * 3600 + 59 * 60 + 59

        /**
         * This time on the rulers' grid, in seconds, and *not* clamped to it — a caller comparing
         * against [MaxTicks] is how the readout knows to say `>` instead of `=`.
         */
        fun ticks(value: Duration): Int = value.roundedSeconds()

        /** The single quantiser, shared by the rulers and the readout so they cannot disagree. */
        fun seconds(value: Duration): Int = ticks(value).coerceIn(0, MaxTicks)
    }
}

@Composable
fun TimeSlider(
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    state: TimeSliderState = rememberTimeSliderState(),
) {
    Column(modifier) {
        Track(
            state = state.hourTrackState,
            itemSize = 150.dp,
            trackAlignment = TrackAlignment.Bottom,
            modifier = Modifier.padding(bottom = 8.dp),
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, _ ->
                Body(item.toString())
            }
        )
        Track(
            state = state.minuteTrackState,
            itemSize = 100.dp,
            showGuidelineDot = true,
            userScrollEnabled = userScrollEnabled,
            itemContent = { item, _ ->
                Body(item.toString())
            }
        )
        Track(
            state = state.secondTrackState,
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
private fun TimeSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val timeState = rememberTimeSliderState()
        LaunchedEffect(timeState) { timeState.moveTo(4.hours, animate = false) }
        Body("Time ${timeState.value}")
        TimeSlider(
            state = timeState
        )
    }
}
