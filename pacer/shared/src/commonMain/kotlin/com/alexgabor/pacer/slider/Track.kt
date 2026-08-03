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
import kotlin.math.roundToInt

private val verticalPadding = 16.dp
private val highLineHeight = 10.dp
private val lowLineHeight = 4.dp
private val lineWidth = 4.dp

enum class TrackAlignment {
    Top, Bottom,
}

/**
 * @param tickUnit the shared pixel grid every track snaps to. It must divide
 * `itemSize / subdivisions` of every track displayed together, otherwise their ticks won't line up.
 */
@Composable
fun <T> Track(
    state: TrackSate<T>,
    modifier: Modifier = Modifier,
    itemSize: Dp = 200.dp,
    trackAlignment: TrackAlignment = TrackAlignment.Bottom,
    firstGuideline: Dp = 64.dp,
    tickUnit: Dp = 20.dp,
    itemContent: @Composable (item: T, subdivision: Int) -> Unit = { item, subdivision -> Text(text = "$item.$subdivision") },
) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current

    // Lay items out on a whole-pixel grid shared by every track. Compose rounds `Modifier.width` to
    // whole pixels, so on fractional densities (Pixel 9 is 2.625 px/dp) an unquantised item width
    // drifts by a fraction of a pixel per item, and tracks with different item sizes drift apart.
    val unitPx = with(density) { tickUnit.toPx() }.roundToInt().coerceAtLeast(1)
    val itemWidthPx = unitPx * (with(density) { itemSize.toPx() } / unitPx).roundToInt()
    val itemWidth = with(density) { itemWidthPx.toDp() }
    val subdivisionPx = itemWidthPx.toFloat() / state.subdivisions
    val strokeWidth = with(density) { (lineWidth.toPx() / 2f).roundToInt() * 2f }
    state.itemSizePx = itemWidthPx.toFloat()

    BoxWithConstraints(modifier) {
        LazyRow(
            state = state.listState,
            contentPadding = PaddingValues(
                start = firstGuideline,
                end = this@BoxWithConstraints.maxWidth - firstGuideline - itemWidth,
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
                    modifier = Modifier.width(itemWidth).drawBehind {
                        when (trackAlignment) {
                            TrackAlignment.Top -> {
                                topRulerLines(
                                    lineColor,
                                    index,
                                    state.trackItems.size,
                                    state.subdivisions,
                                    subdivisionPx,
                                    strokeWidth
                                )
                            }

                            TrackAlignment.Bottom -> {
                                bottomRulerLines(
                                    lineColor,
                                    index,
                                    state.trackItems.size,
                                    state.subdivisions,
                                    subdivisionPx,
                                    strokeWidth
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

    suspend fun animateToIndex(index: Int, subdivision: Int = 0) {
        listState.animateScrollToItem(index, subdivision * (itemSizePx / subdivisions).roundToInt())
    }
}

private fun DrawScope.bottomRulerLines(
    lineColor: Color,
    index: Int,
    count: Int,
    subdivision: Int,
    subdivisionPx: Float,
    strokeWidth: Float,
) {
    drawLine(
        color = lineColor,
        start = Offset(0f, size.height + (verticalPadding - highLineHeight).toPx()),
        end = Offset(0f, size.height + verticalPadding.toPx()),
        strokeWidth = strokeWidth
    )
    if (index < count - 1) {
        repeat(subdivision) { index ->
            val x = subdivisionPx * index
            drawLine(
                color = lineColor,
                start = Offset(x, size.height + (verticalPadding - lowLineHeight).toPx()),
                end = Offset(x, size.height + verticalPadding.toPx()),
                strokeWidth = strokeWidth
            )
        }
    }
}

private fun DrawScope.topRulerLines(
    lineColor: Color,
    index: Int,
    count: Int,
    subdivision: Int,
    subdivisionPx: Float,
    strokeWidth: Float,
) {
    drawLine(
        color = lineColor,
        start = Offset(0f, -verticalPadding.toPx()),
        end = Offset(0f, -verticalPadding.toPx() + highLineHeight.toPx()),
        strokeWidth = strokeWidth
    )
    if (index < count - 1) {
        repeat(subdivision) { index ->
            val x = subdivisionPx * index
            drawLine(
                color = lineColor,
                start = Offset(x, -verticalPadding.toPx()),
                end = Offset(x, -verticalPadding.toPx() + lowLineHeight.toPx()),
                strokeWidth = strokeWidth
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
        center = Offset(firstGuideline.toPx().roundToInt().toFloat(), 8.dp.toPx())
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
            itemSize = 100.dp
        )
    }
}
