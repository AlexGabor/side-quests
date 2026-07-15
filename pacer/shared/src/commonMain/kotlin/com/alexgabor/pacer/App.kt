package com.alexgabor.pacer

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
@Preview
fun App() {
    MaterialTheme {
        BoxWithConstraints(modifier = Modifier.safeDrawingPadding()) {
            this.maxWidth
            Column {
                val track1State = rememberLazyListState()
                val lineColor = MaterialTheme.colorScheme.secondary

                LazyRow(
                    state = track1State,
                    contentPadding = PaddingValues(
                        start = 64.dp,
                        end = this@BoxWithConstraints.maxWidth - 264.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                    flingBehavior = rememberSnapFlingBehavior(
                        lazyListState = track1State,
                        snapPosition = SnapPosition.Start
                    ),
                    modifier = Modifier.fillMaxWidth().drawBehind {
                        val scrollOffset = track1State.firstVisibleItemScrollOffset
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(64.dp.toPx(), 8.dp.toPx())
                        )
                        val lines = size.width / 200.dp.toPx()
                        repeat(lines.roundToInt() + 1) { index ->
                            val x =
                                index * 200.dp.toPx() + 64.dp.toPx() - (scrollOffset % 200.dp.toPx())
                            drawLine(
                                color = lineColor,
                                start = Offset(x, size.height - 16.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                        val shortLines = size.width / 20.dp.toPx()
                        repeat(shortLines.roundToInt() + 1) { index ->
                            val x =
                                index * 20.dp.toPx() + 4.dp.toPx() - (scrollOffset % (20.dp.toPx()))
                            drawLine(
                                color = lineColor,
                                start = Offset(x, size.height - 4.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                    }
                ) {
                    items(60) { index ->
                        Box(
                            modifier = Modifier.width(200.dp)
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

                val track2State = rememberLazyListState()
                LazyRow(
                    state = track2State,
                    contentPadding = PaddingValues(
                        start = 64.dp,
                        end = this@BoxWithConstraints.maxWidth - 84.dp,
                        top = 8.dp,
                        bottom = 72.dp
                    ),
                    flingBehavior = rememberSnapFlingBehavior(
                        lazyListState = track2State,
                        snapPosition = SnapPosition.Start
                    ),
                    modifier = Modifier.fillMaxWidth().drawBehind {
                        val scrollOffset = track2State.firstVisibleItemScrollOffset
                        val shortLines = size.width / 200.dp.toPx()
                        repeat(shortLines.roundToInt() * 10 + 1) { index ->
                            val x =
                                index * (200.dp.toPx() / 10) + 4.dp.toPx() - (scrollOffset % (200.dp.toPx() / 10))
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, 4.dp.toPx()),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                    }
                ) {
                    items(10) { index ->
                        Box(
                            modifier = Modifier.width(20.dp)
                        ) {
                            Text(
                                fontSize = 12.sp,
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
    }
}