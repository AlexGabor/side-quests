package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Sliders(
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color.White)) {
        Text("Distance")
        DistanceSlider()
        Text("Pace")
        PaceSlider()
        Text("Time")
        TimeSlider()
    }
}

@Preview
@Composable
private fun SlidersPreview() {
    Sliders()
}

@Composable
fun PaceSlider(
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Track(
            state = rememberTrackState((1..20).toList(), 1, rememberLazyListState()),
            itemSize = 100.dp,
            modifier = Modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            state = rememberTrackState((0..60 step 10).toList(), 10, rememberLazyListState()),
            itemSize = 200.dp,
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

@Composable
fun DistanceSlider(
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Track(
            state = rememberTrackState((1..800).toList(), 1, rememberLazyListState()),
            itemSize = 100.dp,
            modifier = Modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            state = rememberTrackState((0..100 step 5).toList(), 5, rememberLazyListState()),
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

@Composable
fun TimeSlider(
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Track(
            state = rememberTrackState((1..120).toList(), 1, rememberLazyListState()),
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
            state = rememberTrackState((0..59).toList(), 1, rememberLazyListState()),
            itemSize = 100.dp,
            modifier = Modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            state = rememberTrackState((0..60 step 5).toList(), 5, rememberLazyListState()),
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