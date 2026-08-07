package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.attributes.Body
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


@Composable
fun rememberPaceSliderState(): PaceSliderState {
    val minuteTrackSate: TrackSate<Int> = rememberTrackState(
        trackItems = (2..20).toList(),
        subdivision = 1,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = 4)
    )
    val secondTrackSate: TrackSate<Int> =
        rememberTrackState(
            trackItems = (0..60 step 10).toList(),
            subdivision = 10,
            listState = rememberLazyListState()
        )
    return remember(minuteTrackSate) {
        PaceSliderState(minuteTrackSate, secondTrackSate)
    }
}

data class Pace(
    val minutes: Int,
    val seconds: Int,
)

class PaceSliderState(
    internal val minuteTrackState: TrackSate<Int>,
    internal val secondTrackState: TrackSate<Int>,
) {
    val selectedPace by derivedStateOf {
        val minutes = minuteTrackState.selectedItem + minuteTrackState.selectedSubdivision
        val seconds = secondTrackState.selectedItem + secondTrackState.selectedSubdivision
        Pace(
            minutes = minutes + seconds / 60,
            seconds = seconds % 60,
        )
    }

    suspend fun animateToPace(pace: Pace) {
        val minuteIndex = minuteTrackState.trackItems.indexOf(pace.minutes)
        if (minuteIndex < 0) return
        coroutineScope {
            launch { minuteTrackState.animateToIndex(minuteIndex) }
            launch { secondTrackState.animateToIndex(pace.seconds / 10, pace.seconds % 10) }
        }
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
                Body(text = String.format("%02d", item + subdivision))
            }
        )
    }
}


@Preview
@Composable
private fun PaceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val paceState = rememberPaceSliderState()
        Body(text = "Pace ${paceState.selectedPace.minutes}:${paceState.selectedPace.seconds}")
        PaceSlider(
            state = paceState
        )
    }
}