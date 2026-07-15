package com.alexgabor.pacer

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size


@Composable
fun LazySlider(
    modifier: Modifier = Modifier,
    content: LazySliderScope.() -> Unit,
) {
    val itemProvider = rememberItemProvider(content)
    LazyLayout(
        modifier = modifier.clipToBounds(),
        itemProvider = { itemProvider },
    ) { constraints ->
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        val items = itemProvider.getItems(size)

        val placeables = items.map { (index, offset) ->
            compose(index).map {
                it.measure(constraints)
            } to offset
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (itemPlaceables, position) ->
                itemPlaceables.forEach { placeable ->
                    placeable.placeRelative(
                        x = position.x.toInt(),
                        y = position.y.toInt(),
                    )
                }
            }
        }
    }
}

private data class SliderItem(
    val index: Int,
)

private data class LazySliderItemContent(
    val itemProvider: (index: Int) -> SliderItem,
    val content: @Composable (index: Int) -> Unit,
)

@Composable
private fun rememberItemProvider(
    content: LazySliderScope.() -> Unit,
): ItemProvider {
    val latestContent = rememberUpdatedState(content)
    return remember {
        val scope = derivedStateOf { LazySliderScopeImpl().apply(latestContent.value) }.value
        return@remember derivedStateOf { ItemProvider(scope.intervals) }.value
    }
}

private class ItemProvider(
    private val intervals: IntervalList<LazySliderItemContent>,
) : LazyLayoutItemProvider {
    override val itemCount: Int
        get() = intervals.size

    val items: Map<Int, SliderItem> =
        intervals.mapAll { index, localIndex, item -> index to item.itemProvider(localIndex) }.toMap()


    @Composable
    override fun Item(index: Int, key: Any) {
        val interval = intervals[index]
        val localIntervalIndex = index - interval.startIndex
        interval.value.content(localIntervalIndex)
    }

    fun getItems(
        size: Size,
    ): Map<Int, Offset> {
        return items
            .filterValues { it.index < 10 }
            .mapValues { (_, sliderItem) ->
                Offset(
                    x = sliderItem.index * 110f,
                    y = 0f
                )
            }
    }
}

interface LazySliderScope {
    fun track(
        count: Int,
        itemContent: @Composable (index: Int) -> Unit,
    )
}

private class LazySliderScopeImpl : LazySliderScope {
    val intervals: IntervalList<LazySliderItemContent>
        field = MutableIntervalList<LazySliderItemContent>()

    override fun track(
        count: Int,
        itemContent: @Composable (index: Int) -> Unit,
    ) {
        intervals.addInterval(count, LazySliderItemContent(
            itemProvider = { index -> SliderItem(index) },
            content = itemContent
        ))
    }
}


private fun <T, R> IntervalList<T>.mapAll(block: (Int, Int, T) -> R): List<R> =
    buildList {
        this@mapAll.forEach { interval ->
            repeat(interval.size) { index ->
                add(block(index + interval.startIndex, index, interval.value))
            }
        }
    }