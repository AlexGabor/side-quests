package com.alexgabor.pacer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Slider(
    modifier: Modifier,
) {
    Column(modifier) {
        Track(30)
    }
}

enum class TrackAlignment {
    Top, Bottom,
}

@Composable
fun Track(
    count: Int,
    modifier: Modifier = Modifier,
    trackAlignment: TrackAlignment = TrackAlignment.Bottom,
    firstGuideline: Dp = 64.dp,
    itemSize: Dp = 200.dp,
    subdivision: Int = 10,
) {
    val verticalPadding = 16.dp
    val lineColor = MaterialTheme.colorScheme.secondary
    BoxWithConstraints(modifier) {
        this.maxWidth

        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                start = firstGuideline,
                end = this@BoxWithConstraints.maxWidth - firstGuideline - itemSize,
                top = verticalPadding,
                bottom = verticalPadding
            ),
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = listState,
                snapPosition = SnapPosition.Start
            ),
            modifier = Modifier.fillMaxWidth().drawBehind {
                if (trackAlignment == TrackAlignment.Bottom) {
                    guidelineCircle(lineColor, firstGuideline)
                }
            }
        ) {
            items(count) { index ->
                Box(
                    modifier = Modifier.width(itemSize).drawBehind {
                        when (trackAlignment) {
                            TrackAlignment.Top -> {
                                topRulerLines(lineColor, index, count, subdivision)
                            }

                            TrackAlignment.Bottom -> {
                                bottomRulerLines(lineColor, index, count, subdivision)
                            }
                        }
                    }
                ) {
                    Text(
                        text = "$index",
                        modifier = Modifier.graphicsLayer {
                            translationX = -size.width / 2f
                        }
                    )
                }
            }
        }
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
            count = 30,
            itemSize = 100.dp
        )
        Track(
            count = 30,
            subdivision = 5
        )
        Track(
            count = 30,
            subdivision = 11
        )
        Track(
            count = 30,
            trackAlignment = TrackAlignment.Top,
            subdivision = 11
        )
    }
}

@Preview
@Composable
private fun PaceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        PaceSlider()
    }
}

@Composable
fun PaceSlider(
    modifier: Modifier = Modifier,
) {
    Column {
        Track(
            count = 20,
            itemSize = 100.dp,
            subdivision = 10,
            modifier = modifier
        )
    }
}