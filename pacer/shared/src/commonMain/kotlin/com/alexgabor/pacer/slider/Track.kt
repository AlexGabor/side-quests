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


enum class TrackAlignment {
    Top, Bottom,
}

@Composable
fun <T> Track(
    state: TrackSate<T>,
    modifier: Modifier = Modifier,
    itemSize: Dp = 200.dp,
    trackAlignment: TrackAlignment = TrackAlignment.Bottom,
    firstGuideline: Dp = 64.dp,
    itemContent: @Composable (item: T, subdivision: Int) -> Unit = { item, subdivision -> Text(text = "$item.$subdivision") },
) {
    val verticalPadding = 16.dp
    val lineColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    state.itemSizePx = with(density) { itemSize.toPx() }

    BoxWithConstraints(modifier) {
        LazyRow(
            state = state.listState,
            contentPadding = PaddingValues(
                start = firstGuideline,
                end = this@BoxWithConstraints.maxWidth - firstGuideline - itemSize,
                top = verticalPadding,
                bottom = verticalPadding
            ),
            flingBehavior = rememberSubdivisionFlingBehavior(
                lazyListState = state.listState,
                itemSizePx = state.itemSizePx,
                subdivision = state.subdivisions
            ),
            modifier = Modifier.fillMaxWidth().drawBehind {
                if (trackAlignment == TrackAlignment.Bottom) {
                    guidelineCircle(lineColor, firstGuideline)
                }
            }
        ) {
            itemsIndexed(state.trackItems) { index, item ->
                Box(
                    modifier = Modifier.width(itemSize).drawBehind {
                        when (trackAlignment) {
                            TrackAlignment.Top -> {
                                topRulerLines(
                                    lineColor,
                                    index,
                                    state.trackItems.size,
                                    state.subdivisions
                                )
                            }

                            TrackAlignment.Bottom -> {
                                bottomRulerLines(
                                    lineColor,
                                    index,
                                    state.trackItems.size,
                                    state.subdivisions
                                )
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = -size.width / 2f
                        }
                    ) {
                        itemContent(item, 0)
                    }
                }
            }
        }
        Box(
            modifier = Modifier.padding(start = firstGuideline, top = verticalPadding)
                .graphicsLayer {
                    translationX = -size.width / 2f
                }
                .background(Color.White)
        ) {
            itemContent(state.selectedItem, state.selectedSubdivision)
        }
    }
}

@Composable
fun <T> rememberTrackState(
    trackItems: List<T>,
    subdivision: Int,
    listState: LazyListState,
) = remember(trackItems, subdivision, listState) {
    TrackSate(trackItems, subdivision, listState)
}

class TrackSate<T>(
    val trackItems: List<T>,
    val subdivisions: Int,
    internal val listState: LazyListState,
) {
    var itemSizePx by mutableFloatStateOf(0f)
    val selectedItem by derivedStateOf { trackItems[listState.firstVisibleItemIndex] }
    val selectedSubdivision by derivedStateOf { (listState.firstVisibleItemScrollOffset / (itemSizePx / subdivisions)).toInt() }

    suspend fun animateToIndex(index: Int) {
        listState.animateScrollToItem(index)
    }
}

private fun DrawScope.bottomRulerLines(
    lineColor: Color,
    index: Int,
    count: Int,
    subdivision: Int,
) {
    val verticalPadding = 16.dp
    val highLineHeight = 10.dp
    val lowLineHeight = 4.dp
    drawLine(
        color = lineColor,
        start = Offset(0f, size.height + (verticalPadding - highLineHeight).toPx()),
        end = Offset(0f, size.height + verticalPadding.toPx()),
        strokeWidth = 4.dp.toPx()
    )
    if (index < count - 1) {
        val distance = size.width / subdivision
        repeat(subdivision) { index ->
            val x = distance * index
            drawLine(
                color = lineColor,
                start = Offset(x, size.height + (verticalPadding - lowLineHeight).toPx()),
                end = Offset(x, size.height + verticalPadding.toPx()),
                strokeWidth = 4.dp.toPx()
            )
        }
    }
}

private fun DrawScope.topRulerLines(
    lineColor: Color,
    index: Int,
    count: Int,
    subdivision: Int,
) {
    val verticalPadding = 16.dp
    val highLineHeight = 10.dp
    val lowLineHeight = 4.dp
    drawLine(
        color = lineColor,
        start = Offset(0f, -verticalPadding.toPx()),
        end = Offset(0f, -verticalPadding.toPx() + highLineHeight.toPx()),
        strokeWidth = 4.dp.toPx()
    )
    if (index < count - 1) {
        val distance = size.width / subdivision
        repeat(subdivision) { index ->
            val x = distance * index
            drawLine(
                color = lineColor,
                start = Offset(x, -verticalPadding.toPx()),
                end = Offset(x, -verticalPadding.toPx() + lowLineHeight.toPx()),
                strokeWidth = 4.dp.toPx()
            )
        }
    }
}

private fun DrawScope.guidelineCircle(
    lineColor: Color,
    firstGuideline: Dp,
) {
    drawCircle(
        color = lineColor,
        radius = 4.dp.toPx(),
        center = Offset(firstGuideline.toPx(), 8.dp.toPx())
    )
}


@Preview
@Composable
private fun TrackPreview() {
    Column(Modifier.background(Color.White)) {
        Track(
            state = rememberTrackState((1..30).toList(), 1, rememberLazyListState()),
            itemSize = 100.dp
        )
        Track(
            state = rememberTrackState((1..30).toList(), 5, rememberLazyListState()),
        )
        Track(
            state = rememberTrackState((1..30).toList(), 11, rememberLazyListState()),
        )
        Track(
            state = rememberTrackState((1..30).toList(), 11, rememberLazyListState()),
            trackAlignment = TrackAlignment.Top,
        )
    }
}
