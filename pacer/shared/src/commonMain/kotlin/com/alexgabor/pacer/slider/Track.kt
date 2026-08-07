package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

private val verticalPadding = 16.dp
private val highLineHeight = 10.dp
private val lowLineHeight = 4.dp
private val lineWidth = 4.dp

/** A fling crosses ruler lines far faster than the vibrator can play them, so clicks are spaced out. */
private val minHapticInterval = 24.milliseconds

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
    showGuidelineDot: Boolean = false,
    userScrollEnabled: Boolean = true,
    tickUnit: Dp = 10.dp,
    itemContent: @Composable (item: T, subdivision: Int) -> Unit = { item, subdivision ->
        Body("$item.$subdivision")
    },
) {
    val lineColor = RisoTheme.colors.content
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

    TrackHaptics(state)

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
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxWidth().drawBehind {
                if (showGuidelineDot) {
                    guidelineCircle(lineColor, firstGuideline, trackAlignment)
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
        ) {
            itemContent(state.selectedItem, state.selectedSubdivision)
        }
    }
}

/**
 * Clicks once for every ruler line that passes the guideline — a firm tick for an item, a lighter
 * one for a subdivision. Only scrolls the user started click, so a track that another slider is
 * driving programmatically stays quiet.
 */
@Composable
private fun <T> TrackHaptics(state: TrackSate<T>) {
    val haptics = LocalHapticFeedback.current
    var userScrolling by remember(state) { mutableStateOf(false) }

    LaunchedEffect(state, haptics) {
        launch {
            state.listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) userScrolling = true
            }
        }
        launch {
            // Covers the whole gesture: the drag itself and the fling it hands off to.
            snapshotFlow { state.listState.isScrollInProgress }
                .collect { scrolling -> if (!scrolling) userScrolling = false }
        }
        var lastClick = TimeSource.Monotonic.markNow() - minHapticInterval
        snapshotFlow { state.tick }
            .drop(1)
            .collect { tick ->
                if (!userScrolling || lastClick.elapsedNow() < minHapticInterval) return@collect
                lastClick = TimeSource.Monotonic.markNow()
                haptics.performHapticFeedback(
                    if (tick % state.subdivisions == 0) HapticFeedbackType.SegmentTick
                    else HapticFeedbackType.SegmentFrequentTick
                )
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

    /** Position of the guideline counted in ruler lines from the start of the track. */
    internal val tick by derivedStateOf {
        listState.firstVisibleItemIndex * subdivisions + selectedSubdivision
    }

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
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height + verticalPadding.toPx()),
            end = Offset(size.width, size.height + verticalPadding.toPx()),
            strokeWidth = strokeWidth / 2
        )
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
        drawLine(
            color = lineColor,
            start = Offset(0f, -verticalPadding.toPx()),
            end = Offset(size.width, -verticalPadding.toPx()),
            strokeWidth = strokeWidth / 2
        )
    }
}

private fun DrawScope.guidelineCircle(
    lineColor: Color,
    firstGuideline: Dp,
    trackAlignment: TrackAlignment,
) {
    when (trackAlignment) {
        TrackAlignment.Bottom -> {
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(firstGuideline.toPx().roundToInt().toFloat(), 8.dp.toPx())
            )
        }
        TrackAlignment.Top -> {
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(firstGuideline.toPx().roundToInt().toFloat(), size.height - 8.dp.toPx())
            )
        }
    }
}


@Preview
@Composable
private fun TrackPreview() {
    Column(Modifier.background(RisoTheme.colors.paper)) {
        Track(
            state = rememberTrackState((1..30).toList(), 1, rememberLazyListState()),
            itemSize = 100.dp,
            showGuidelineDot = true,
        )
        Track(
            state = rememberTrackState((1..30).toList(), 5, rememberLazyListState()),
            showGuidelineDot = true,
        )
        Track(
            state = rememberTrackState((1..30).toList(), 11, rememberLazyListState()),
            showGuidelineDot = true,
        )
        Track(
            state = rememberTrackState((1..30).toList(), 11, rememberLazyListState()),
            trackAlignment = TrackAlignment.Top,
            itemSize = 100.dp,
            showGuidelineDot = true,
        )
    }
}
