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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun Slider(
    modifier: Modifier,
) {
    Column(modifier = modifier.safeDrawingPadding()) {
        Track()
    }
}

@Composable
fun Track(
    modifier: Modifier = Modifier,
    firstGuideline: Dp = 64.dp,
    itemSize: Dp = 200.dp,
    subdivision: Int = 10,
) {
    val lineColor = MaterialTheme.colorScheme.secondary
    BoxWithConstraints(modifier) {
        this.maxWidth

        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                start = firstGuideline,
                end = this@BoxWithConstraints.maxWidth - firstGuideline - itemSize,
                top = 16.dp,
                bottom = 16.dp
            ),
            flingBehavior = rememberSnapFlingBehavior(
                lazyListState = listState,
                snapPosition = SnapPosition.Start
            ),
            modifier = Modifier.fillMaxWidth().drawBehind {
                val scrollOffset = listState.firstVisibleItemScrollOffset
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(firstGuideline.toPx(), 8.dp.toPx())
                )
                val lineCount = size.width / itemSize.toPx() * subdivision
                repeat(lineCount.roundToInt() + 2) { index ->
                    val x = index * (itemSize.toPx() / subdivision) + firstGuideline.toPx()
                    val xScrolled = x - scrollOffset
                    val height = if (index % subdivision == 0) 10.dp else 4.dp
                    drawLine(
                        color = lineColor,
                        start = Offset(xScrolled, size.height - height.toPx()),
                        end = Offset(xScrolled, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
        ) {
            items(60) { index ->
                Box(
                    modifier = Modifier.width(itemSize)
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

@Preview
@Composable
fun Track1Preview() {
    Column(Modifier.background(Color.White)) {
        Track(
            itemSize = 100.dp
        )
        Track(
            subdivision = 5
        )
        Track(
            subdivision = 11
        )
    }
}