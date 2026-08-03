package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberTimeSliderState(): TimeSliderState {
    val hourTrackState = rememberTrackState(
        trackItems = (0..120).toList(),
        subdivision = 1,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = 4)
    )
    val minuteTrackState = rememberTrackState(
        trackItems = (0..59).toList(),
        subdivision = 1,
        listState = rememberLazyListState()
    )
    val secondTrackState = rememberTrackState(
        trackItems = (0..60 step 5).toList(),
        subdivision = 5,
        listState = rememberLazyListState()
    )
    return remember(hourTrackState, minuteTrackState, secondTrackState) {
        TimeSliderState(hourTrackState, minuteTrackState, secondTrackState)
    }
}

data class Time(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

class TimeSliderState(
    internal val hourTrackState: TrackSate<Int>,
    internal val minuteTrackState: TrackSate<Int>,
    internal val secondTrackState: TrackSate<Int>,
) {
    val selectedTime by derivedStateOf {
        val hours = hourTrackState.selectedItem + hourTrackState.selectedSubdivision
        val minutes = minuteTrackState.selectedItem + minuteTrackState.selectedSubdivision
        val seconds = secondTrackState.selectedItem + secondTrackState.selectedSubdivision

        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        Time(
            hours = totalSeconds / 3600,
            minutes = (totalSeconds % 3600) / 60,
            seconds = totalSeconds % 60
        )
    }

    suspend fun animateToTime(time: Time) {
        coroutineScope {
            launch { hourTrackState.animateToIndex(time.hours) }
            launch { minuteTrackState.animateToIndex(time.minutes) }
            launch { secondTrackState.animateToIndex(time.seconds / 5, time.seconds % 5) }
        }
    }
}

@Composable
fun TimeSlider(
    modifier: Modifier = Modifier,
    state: TimeSliderState = rememberTimeSliderState(),
) {
    Column(modifier) {
        Track(
            state = state.hourTrackState,
            itemSize = 200.dp,
            trackAlignment = TrackAlignment.Top,
            modifier = Modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            state = state.minuteTrackState,
            itemSize = 100.dp,
            modifier = Modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            state = state.secondTrackState,
            itemSize = 100.dp,
            trackAlignment = TrackAlignment.Top,
            modifier = Modifier,
            itemContent = { item, subdivision ->
                Text(
                    text = String.format("%02d", item + subdivision),
                )
            }
        )
    }
}

@Preview
@Composable
private fun TimeSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val timeState = rememberTimeSliderState()
        Text("Time ${timeState.selectedTime.hours}:${timeState.selectedTime.minutes}:${timeState.selectedTime.seconds}")
        TimeSlider(
            state = timeState
        )
    }
}
