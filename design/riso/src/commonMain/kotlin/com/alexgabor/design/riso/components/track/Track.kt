package com.alexgabor.design.riso.components.track

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

private val verticalPadding = 16.dp
private val highLineHeight = 10.dp
private val lowLineHeight = 4.dp

/** A fling crosses ruler lines far faster than the vibrator can play them, so clicks are spaced out. */
private val minHapticInterval = 24.milliseconds

enum class TrackAlignment {
    Top, Bottom,
}

/**
 * @param tickUnit the shared pixel grid every track snaps to. It must divide
 * `itemSize / subdivisions` of every track displayed together, otherwise their ticks won't line up.
 * @param showSelectedLabel whether [itemContent] for the selected item and subdivision is also drawn at
 * the guideline. Turn it off for a ruler that rests between lines (see [TrackState.moveTo]), where that
 * label would sit next to, not on, the ruler's own.
 */
@Composable
fun <T> Track(
    state: TrackState<T>,
    modifier: Modifier = Modifier,
    itemSize: Dp = 200.dp,
    trackAlignment: TrackAlignment = TrackAlignment.Bottom,
    firstGuideline: Dp = 64.dp,
    showGuidelineDot: Boolean = false,
    userScrollEnabled: Boolean = true,
    tickUnit: Dp = 10.dp,
    showSelectedLabel: Boolean = true,
    itemContent: @Composable (item: T, subdivision: Int) -> Unit = { item, subdivision ->
        Body("$item.$subdivision")
    },
) {
    val lineColor = RisoTheme.colors.content
    val dimens = RisoTheme.dimens
    val density = LocalDensity.current

    // Lay items out on a whole-pixel grid shared by every track. Compose rounds `Modifier.width` to
    // whole pixels, so on fractional densities (Pixel 9 is 2.625 px/dp) an unquantised item width
    // drifts by a fraction of a pixel per item, and tracks with different item sizes drift apart.
    val unitPx = with(density) { tickUnit.toPx() }.roundToInt().coerceAtLeast(1)
    val itemWidthPx = unitPx * (with(density) { itemSize.toPx() } / unitPx).roundToInt()
    val itemWidth = with(density) { itemWidthPx.toDp() }
    val subdivisionPx = itemWidthPx.toFloat() / state.subdivisions
    val strokeWidth = with(density) { dimens.lineWidth.toPx().roundToInt() * 2f }
    state.itemSizePx = itemWidthPx.toFloat()

    TrackHaptics(state)

    BoxWithConstraints(modifier) {
        LazyRow(
            state = state.listState,
            contentPadding = PaddingValues(
                start = firstGuideline,
                // Enough to scroll the end of the ruler onto the guideline: the last item's leading
                // edge, or the state's end inside it. A track narrower than that can't, and clamping
                // degrades to an unreachable tail instead of throwing.
                end = (this@BoxWithConstraints.maxWidth - firstGuideline - with(density) { state.tailPx(subdivisionPx).toDp() })
                    .coerceAtLeast(0.dp),
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
                    modifier = Modifier.width(itemWidth).drawWithContent {
                        // Between ruler lines (see TrackState.moveTo) by drawing alone: a layer's
                        // translation would count as the layout moving.
                        translate(left = -state.drawnFraction * subdivisionPx) {
                            this@drawWithContent.drawContent()
                        }
                    }.drawBehind {
                        when (trackAlignment) {
                            TrackAlignment.Top -> {
                                topRulerLines(
                                    lineColor,
                                    state.ticksDrawnIn(index),
                                    state.subdivisions,
                                    subdivisionPx,
                                    strokeWidth
                                )
                            }

                            TrackAlignment.Bottom -> {
                                bottomRulerLines(
                                    lineColor,
                                    state.ticksDrawnIn(index),
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
        if (showSelectedLabel) {
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
}

/**
 * Clicks once for every ruler line that passes the guideline — a firm tick for an item, a lighter
 * one for a subdivision. Only scrolls the user started click, so a track that another slider is
 * driving programmatically stays quiet.
 */
@Composable
private fun <T> TrackHaptics(state: TrackState<T>) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state, haptics) {
        var lastClick = TimeSource.Monotonic.markNow() - minHapticInterval
        snapshotFlow { state.tick }
            .drop(1)
            .collect { tick ->
                if (!state.isUserScrolling || lastClick.elapsedNow() < minHapticInterval) {
                    return@collect
                }
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
    endTick: Float? = null,
) = remember(trackItems, subdivision, listState, endTick) {
    TrackState(trackItems, subdivision, listState, endTick)
}

/**
 * @param endTick where the ruler ends, in ruler lines from its start, when that isn't the last item's
 * leading edge: a point inside the last item, between lines if need be (a book that is 3:15 long on a
 * ruler of minutes). The ruler is drawn up to it, closed by a long line, and scrolls no further.
 */
class TrackState<T>(
    val trackItems: List<T>,
    val subdivisions: Int,
    internal val listState: LazyListState = LazyListState(),
    endTick: Float? = null,
) {
    var itemSizePx by mutableFloatStateOf(0f)

    /** Where the ruler ends, in ruler lines: [endTick] kept inside the last item, or its leading edge. */
    val end: Float = run {
        val lastItem = ((trackItems.size - 1) * subdivisions).coerceAtLeast(0).toFloat()
        endTick?.coerceIn(lastItem, lastItem + subdivisions - 1e-3f) ?: lastItem
    }

    /**
     * The furthest ruler line the guideline reaches: the last item's leading edge, or the last line
     * before [end]. The end contentPadding stops there, so lines after it can't be scrolled onto it.
     */
    val maxTick: Int get() = end.toInt()

    /** The ruler is scrolled as far as it goes: [end], not [maxTick], is under the guideline. */
    val isAtEnd: Boolean get() = !listState.canScrollForward

    /**
     * How many ruler lines of item [index] are drawn, counted from its leading edge: every one up to
     * the next item, a part of one for the item [end] falls in, none past it.
     */
    internal fun ticksDrawnIn(index: Int): Float = (end - index * subdivisions).coerceIn(0f, subdivisions.toFloat())

    /** The width from [end] to the end of the last item, which the end padding has to make up for. */
    internal fun tailPx(subdivisionPx: Float): Float = (trackItems.size * subdivisions - end) * subdivisionPx

    /** Position of the guideline counted in ruler lines from the start of the track. */
    val tick: Int by derivedStateOf {
        val size = itemSizePx
        // Before the first layout there is no pixel grid to divide by. Reading zero rather than
        // dividing by it is the point: Infinity.toInt() is Int.MAX_VALUE, and a restored scroll
        // offset used to turn into exactly that.
        val subdivision = if (size <= 0f) {
            0
        } else {
            (listState.firstVisibleItemScrollOffset / (size / subdivisions))
                .toInt()
                .coerceIn(0, subdivisions - 1)
        }
        listState.firstVisibleItemIndex * subdivisions + subdivision
    }

    val selectedItem: T get() = trackItems[tick / subdivisions]

    val selectedSubdivision: Int get() = tick % subdivisions

    /**
     * True from the moment the user touches or wheels this track until it has come to rest — the
     * fling after a drag, and the animation after a wheel or trackpad scroll, included.
     */
    var isUserScrolling by mutableStateOf(false)
        private set

    /**
     * True from an [interruptUserScroll] until the scroll it let go of has come to rest, or a new
     * drag starts. Where the ruler happens to be in that time is not something the user chose.
     */
    var isInterrupted by mutableStateOf(false)
        private set

    /** Set for the length of a [moveToTick], so its scroll isn't mistaken for the user's. */
    private var isMovingToTick by mutableStateOf(false)

    /** A finger is on the track, as opposed to the fling it hands off to. */
    private var isDragging by mutableStateOf(false)

    /**
     * Lets go of the fling the user left running, so the value it was heading for can be replaced.
     * The fling itself runs on until the next [moveToTick], which takes the scroll over from it.
     *
     * Does nothing while a finger is still down: a drag outranks any scroll this track starts, so
     * there would be nothing to take over.
     */
    fun interruptUserScroll() {
        if (!isUserScrolling || isDragging) return
        isUserScrolling = false
        isInterrupted = true
    }

    /**
     * Watches the gesture. Deliberately not tied to the composition: a card scrolled out of the
     * list is disposed mid-drag, and a flag that died with it would leave the value sync guessing.
     *
     * Any scroll this track didn't start itself is the user's. A drag is caught as it starts, a
     * touch before anything has moved; the scroll check is what catches the rest — a mouse wheel
     * or a trackpad scrolls without any drag interaction at all, so on the web and the desktop
     * that is most of what the user does.
     */
    suspend fun trackUserScroll(): Unit = coroutineScope {
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        // The finger takes the ruler from where it is laid out.
                        drawnFraction = 0f
                        isDragging = true
                        isInterrupted = false
                        isUserScrolling = true
                    }
                    is DragInteraction.Stop, is DragInteraction.Cancel -> isDragging = false
                }
            }
        }
        launch {
            // Read together, in one snapshot, so a moveToTick can never be seen scrolling without
            // also being seen as the one doing it. An interrupted fling still scrolling isn't the
            // user's any more either.
            snapshotFlow { listState.isScrollInProgress && !isMovingToTick && !isInterrupted }
                .collect { userScroll -> if (userScroll) isUserScrolling = true }
        }
        launch {
            // Covers the whole gesture: the drag itself and the fling it hands off to.
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling ->
                    if (!scrolling) {
                        isUserScrolling = false
                        isInterrupted = false
                    }
                }
        }
    }

    /**
     * How far past [tick] the ruler is drawn, in ruler lines, from 0 until 1. Set by [moveTo]; the
     * items are shifted by drawing them, so the layout (and the [tick] it reads) only moves line by line.
     */
    internal var drawnFraction by mutableFloatStateOf(0f)
        private set

    suspend fun moveToTick(tick: Int, animate: Boolean) = moveTo(tick.toFloat(), animate)

    /**
     * Like [moveToTick], but [ticks] may fall between ruler lines, so a ruler following a value that
     * changes continuously (a playing track's position) can glide instead of stepping line by line.
     *
     * Only the whole lines scroll the list; the rest is drawn as an offset. A ruler gliding by scrolling
     * (or by a layer translation) would move its layout several times a second, and every layout move is
     * reported to accessibility services, so the screen would never be idle for them (or for UI
     * automation, which waits for that).
     */
    suspend fun moveTo(ticks: Float, animate: Boolean) {
        val target = ticks.coerceIn(0f, end)
        val whole = target.toInt()
        val size = awaitItemSize()
        val index = whole / subdivisions
        val offset = ((whole % subdivisions) * (size / subdivisions)).roundToInt()
        drawnFraction = target - whole
        if (!animate && listState.firstVisibleItemIndex == index && listState.firstVisibleItemScrollOffset == offset) return
        isMovingToTick = true
        try {
            if (animate) listState.animateScrollToItem(index, offset)
            else listState.scrollToItem(index, offset)
        } finally {
            isMovingToTick = false
        }
    }

    /**
     * A track only knows its pixel grid once it has been laid out, and a card in a lazy list may
     * not be laid out for a long time, or ever. Waiting is the honest answer — the caller is a
     * `collectLatest`, so a newer target simply replaces the one that is waiting.
     */
    private suspend fun awaitItemSize(): Float =
        itemSizePx.takeIf { it > 0f } ?: snapshotFlow { itemSizePx }.first { it > 0f }
}

/**
 * Item lines along the bottom: a long line at the item's leading edge, short ones at its subdivisions
 * and a rule joining them, for the first [ticks] ruler lines of the item. When the ruler ends inside
 * the item, a long line closes it there.
 */
private fun DrawScope.bottomRulerLines(
    lineColor: Color,
    ticks: Float,
    subdivision: Int,
    subdivisionPx: Float,
    strokeWidth: Float,
) {
    val base = size.height + verticalPadding.toPx()
    rulerLines(lineColor, ticks, subdivision, subdivisionPx, strokeWidth, base, up = true)
}

/** As [bottomRulerLines], hanging from the top. */
private fun DrawScope.topRulerLines(
    lineColor: Color,
    ticks: Float,
    subdivision: Int,
    subdivisionPx: Float,
    strokeWidth: Float,
) {
    val base = -verticalPadding.toPx()
    rulerLines(lineColor, ticks, subdivision, subdivisionPx, strokeWidth, base, up = false)
}

private fun DrawScope.rulerLines(
    lineColor: Color,
    ticks: Float,
    subdivision: Int,
    subdivisionPx: Float,
    strokeWidth: Float,
    base: Float,
    up: Boolean,
) {
    val direction = if (up) -1f else 1f
    fun line(x: Float, height: Dp, width: Float = strokeWidth) = drawLine(
        color = lineColor,
        start = Offset(x, base),
        end = Offset(x, base + direction * height.toPx()),
        strokeWidth = width,
    )
    line(0f, highLineHeight)
    if (ticks <= 0f) return
    repeat(subdivision) { index ->
        if (index <= ticks) line(subdivisionPx * index, lowLineHeight)
    }
    val endX = ticks * subdivisionPx
    drawLine(
        color = lineColor,
        start = Offset(0f, base),
        end = Offset(endX, base),
        strokeWidth = strokeWidth / 2
    )
    // Inside the item: the ruler's own end, not the next item's leading edge.
    if (ticks < subdivision) line(endX, highLineHeight)
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
