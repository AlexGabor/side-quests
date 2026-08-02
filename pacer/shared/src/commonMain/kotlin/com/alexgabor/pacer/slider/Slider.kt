package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun Sliders(
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color.White)) {
        Text("Distance")
        DistanceSlider()
        Text("Pace")
        PaceSlider()
    }
}

enum class TrackAlignment {
    Top, Bottom,
}

@Composable
fun <T> Track(
    trackItems: List<T>,
    modifier: Modifier = Modifier,
    trackAlignment: TrackAlignment = TrackAlignment.Bottom,
    firstGuideline: Dp = 64.dp,
    itemSize: Dp = 200.dp,
    subdivision: Int = 10,
    itemContent: @Composable (item: T, subdivision: Int) -> Unit = { item, subdivision -> Text(text = "$item.$subdivision") },
) {
    val verticalPadding = 16.dp
    val lineColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val itemSizePx = with(density) { itemSize.toPx() }
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
            flingBehavior = rememberSubdivisionFlingBehavior(
                lazyListState = listState,
                itemSizePx = itemSizePx,
                subdivision = subdivision
            ),
            modifier = Modifier.fillMaxWidth().drawBehind {
                if (trackAlignment == TrackAlignment.Bottom) {
                    guidelineCircle(lineColor, firstGuideline)
                }
            }
        ) {
            itemsIndexed(trackItems) { index, item ->
                Box(
                    modifier = Modifier.width(itemSize).drawBehind {
                        when (trackAlignment) {
                            TrackAlignment.Top -> {
                                topRulerLines(lineColor, index, trackItems.size, subdivision)
                            }

                            TrackAlignment.Bottom -> {
                                bottomRulerLines(lineColor, index, trackItems.size, subdivision)
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
            val index by remember { derivedStateOf {  listState.firstVisibleItemIndex } }
            val subdivision by remember { derivedStateOf { (listState.firstVisibleItemScrollOffset / (itemSizePx / subdivision)).toInt() } }
            itemContent(trackItems[index], subdivision)
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
            trackItems = (1..30).toList(),
            itemSize = 100.dp
        )
        Track(
            trackItems = (1..30).toList(),
            subdivision = 5
        )
        Track(
            trackItems = (1..30).toList(),
            subdivision = 11
        )
        Track(
            trackItems = (1..30).toList(),
            trackAlignment = TrackAlignment.Top,
            subdivision = 11
        )
    }
}

@Preview
@Composable
private fun PaceSliderPreview() {
    Column(Modifier.background(Color.White)) {
        Text("Distance")
        DistanceSlider()
        Text("Pace")
        PaceSlider()
    }
}

@Composable
fun PaceSlider(
    modifier: Modifier = Modifier,
) {
    Column {
        Track(
            trackItems = (1..20).toList(),
            itemSize = 100.dp,
            subdivision = 1,
            modifier = modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            trackItems = (0..60 step 10).toList(),
            itemSize = 200.dp,
            trackAlignment = TrackAlignment.Top,
            subdivision = 10,
            modifier = modifier,
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
    Column {
        Track(
            trackItems = (0..100).toList(),
            itemSize = 100.dp,
            subdivision = 1,
            modifier = modifier,
            itemContent = { item, _ ->
                Text(
                    text = item.toString(),
                )
            }
        )
        Track(
            trackItems = (0..100 step 5).toList(),
            itemSize = 100.dp,
            trackAlignment = TrackAlignment.Top,
            subdivision = 5,
            modifier = modifier,
            itemContent = { item, subdivision ->
                Text(
                    text = String.format("%02d", item + subdivision),
                )
            }
        )
    }
}