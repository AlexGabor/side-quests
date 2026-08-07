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
import com.alexgabor.pacer.track.Track
import com.alexgabor.pacer.track.TrackAlignment
import com.alexgabor.pacer.track.TrackSate
import com.alexgabor.pacer.track.rememberTrackState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberDistanceSliderState(): DistanceSliderState {
    val wholeTrackState: TrackSate<Int> = rememberTrackState(
        trackItems = (0..800).toList(),
        subdivision = 1,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = 42)
    )
    val fractionTrackState: TrackSate<Int> = rememberTrackState(
        trackItems = (0..100 step 5).toList(),
        subdivision = 5,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = 4)
    )
    return remember(wholeTrackState, fractionTrackState) {
        DistanceSliderState(wholeTrackState, fractionTrackState)
    }
}

data class Distance(
    val whole: Int,
    val fraction: Int,
)

class DistanceSliderState(
    internal val kilometerTrackState: TrackSate<Int>,
    internal val fractionTrackState: TrackSate<Int>,
) {
    val selectedDistance by derivedStateOf {
        val whole = kilometerTrackState.selectedItem + kilometerTrackState.selectedSubdivision
        val fraction = fractionTrackState.selectedItem + fractionTrackState.selectedSubdivision
        Distance(
            whole = whole + fraction / 100,
            fraction = fraction % 100,
        )
    }

    suspend fun animateToDistance(distance: Distance) {
        coroutineScope {
            launch {
                kilometerTrackState.animateToIndex(distance.whole)
            }
            launch {
                fractionTrackState.animateToIndex(distance.fraction / 5, distance.fraction % 5)
            }
        }
    }
}

@Composable
fun DistanceSlider(
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    state: DistanceSliderState = rememberDistanceSliderState(),
) {
    Column(modifier) {
        Track(
            state = state.kilometerTrackState,
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
                Body(String.format("%02d", item + subdivision))
            }
        )
    }
}

@Preview
@Composable
private fun DistanceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        val distanceState = rememberDistanceSliderState()
        Body("Distance ${distanceState.selectedDistance.whole}.${distanceState.selectedDistance.fraction}")
        DistanceSlider(
            state = distanceState
        )
    }
}
