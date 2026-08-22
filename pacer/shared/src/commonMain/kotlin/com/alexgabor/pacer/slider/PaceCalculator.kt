package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.Card
import com.alexgabor.pacer.track.TrackSate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Metric {
    Distance, Pace, Time
}

/**
 * How the number on a card relates to the value behind it.
 *
 * [Greater] means the ruler has run out of room and is parked at its end: the real value is past
 * anything it can draw. The value itself is kept intact, so scrolling back into range shows the
 * true figure rather than resuming from the end of the ruler.
 */
enum class Comparison(val text: String) {
    Equal("="),
    Greater(">"),
    Less("<"),
}

/**
 * The run being described: a distance, a pace and a time, any one of which is computed from the
 * other two.
 *
 * Distance and pace are held in kilometres whatever unit is on screen; see [Distance].
 */
class PaceCalculatorState(
    val distanceSliderState: DistanceSliderState = DistanceSliderState(),
    val paceSliderState: PaceSliderState = PaceSliderState(),
    val timeSliderState: TimeSliderState = TimeSliderState(),
    distance: Distance = Distance(42.20),
    pace: Duration = 6.minutes,
    time: Duration = 4.hours + 13.minutes + 12.seconds,
    selectedMetric: Metric = Metric.Pace,
    selectedUnit: DistanceUnit = DistanceUnit.Kilometers,
) {
    var distance by mutableStateOf(distance)
        private set

    /** Per kilometre, whatever unit is on screen. */
    var pace by mutableStateOf(pace)
        private set

    var time by mutableStateOf(time)
        private set

    var selectedMetric by mutableStateOf(selectedMetric)
        private set

    var selectedUnit by mutableStateOf(selectedUnit)
        private set

    internal val distanceOnSlider: Double get() = distance.inUnit(selectedUnit)

    internal val paceOnSlider: Duration get() = pace * selectedUnit.kilometers

    internal val timeOnSlider: Duration get() = time

    val displayedDistance: String get() {
        val hundredths = DistanceSliderState.hundredths(distanceOnSlider)
        return "${hundredths / 100}.${(hundredths % 100).twoDigits()} ${selectedUnit.text}"
    }

    val displayedPace: String get() {
        val seconds = PaceSliderState.seconds(paceOnSlider)
        return "${seconds / 60}:${(seconds % 60).twoDigits()} ${selectedUnit.paceText}"
    }

    val displayedTime: String get() {
        val seconds = TimeSliderState.seconds(time)
        return "${seconds / 3600}h ${((seconds % 3600) / 60).twoDigits()}m " +
            "${(seconds % 60).twoDigits()}s"
    }

    val distanceComparison: Comparison
        get() = comparison(
            DistanceSliderState.ticks(distanceOnSlider),
            DistanceSliderState.MaxTicks,
        )

    val paceComparison: Comparison
        get() = comparison(PaceSliderState.ticks(paceOnSlider), PaceSliderState.MaxTicks)

    val timeComparison: Comparison
        get() = comparison(TimeSliderState.ticks(time), TimeSliderState.MaxTicks)

    fun selectMetric(metric: Metric) {
        if (metric == selectedMetric) return
        selectedMetric = metric
        recompute()
    }

    /**
     * Distance and pace are only ever "per unit" — the sliders carry no unit of their own — so a
     * change of unit is a change of the numbers on them, not just of the labels. Because the values
     * themselves are held in kilometres there is nothing to convert: the sliders read the new unit
     * and re-draw, and the run being described is untouched.
     */
    fun selectUnit(unit: DistanceUnit) {
        selectedUnit = unit
    }

    /** Whichever metric is selected is the one computed; the other two are what the user sets. */
    private fun recompute() {
        when (selectedMetric) {
            // A pace of zero is infinite speed, and no distance follows from it.
            Metric.Distance -> if (pace > Duration.ZERO) updateDistance(Distance(time / pace))
            // And no pace follows from standing still.
            Metric.Pace -> if (distance.kilometers > 0.0) updatePace(time / distance.kilometers)
            Metric.Time -> updateTime(pace * distance.kilometers)
        }
    }

    /**
     * These keep non-finite values out of the fields but deliberately don't clamp: a value
     * past the end of its ruler stays exact, and [Comparison] is how the card admits that the ruler
     * can't show all of it.
     */
    private fun updateDistance(value: Distance) {
        if (value.kilometers.isFinite() && value.kilometers >= 0.0) distance = value
    }

    private fun updatePace(value: Duration) {
        if (value.isFinite() && value >= Duration.ZERO) pace = value
    }

    private fun updateTime(value: Duration) {
        if (value.isFinite() && value >= Duration.ZERO) time = value
    }

    internal fun onDistanceScrolled(displayed: Double) {
        if (selectedMetric == Metric.Distance) return
        updateDistance(Distance.of(displayed, selectedUnit))
        recompute()
    }

    internal fun onPaceScrolled(displayed: Duration) {
        if (selectedMetric == Metric.Pace) return
        updatePace(displayed / selectedUnit.kilometers)
        recompute()
    }

    internal fun onTimeScrolled(displayed: Duration) {
        if (selectedMetric == Metric.Time) return
        updateTime(displayed)
        recompute()
    }

    internal val tracks: List<TrackSate<Int>>
        get() = distanceSliderState.tracks + paceSliderState.tracks + timeSliderState.tracks

    /**
     * Keeps the values and the sliders showing the same run, in both directions.
     *
     * Lives with the state rather than with the layout so that reflowing the screen — rotating into
     * two panes, say — doesn't tear the sync down and re-animate all three sliders.
     */
    suspend fun sync(): Unit = coroutineScope {
        tracks.forEach { launch { it.trackUserScroll() } }

        launch {
            collectUserScroll(
                distanceSliderState::isUserScrolling,
                distanceSliderState::value,
                ::onDistanceScrolled,
            )
        }
        launch {
            collectUserScroll(
                paceSliderState::isUserScrolling,
                paceSliderState::value,
                ::onPaceScrolled,
            )
        }
        launch {
            collectUserScroll(
                timeSliderState::isUserScrolling,
                timeSliderState::value,
                ::onTimeScrolled,
            )
        }

        launch {
            syncDown(
                ::distanceOnSlider,
                distanceSliderState::isUserScrolling,
                distanceSliderState::moveTo,
            )
        }
        launch {
            syncDown(::paceOnSlider, paceSliderState::isUserScrolling, paceSliderState::moveTo)
        }
        launch {
            syncDown(::timeOnSlider, timeSliderState::isUserScrolling, timeSliderState::moveTo)
        }
    }
}

private fun comparison(ticks: Int, maxTicks: Int): Comparison = when {
    ticks > maxTicks -> Comparison.Greater
    ticks < 0 -> Comparison.Less
    else -> Comparison.Equal
}

/**
 * Reports what the user's own finger did to a slider, and only that — a slider being driven
 * programmatically never emits a [androidx.compose.foundation.interaction.DragInteraction], so the
 * two directions can't chase each other.
 *
 * Reporting live rather than only once the gesture ends is what makes the other two cards move
 * under the finger. The falling edge is reported too: the settling scroll and the end of the
 * gesture can land in the same frame, and dropping that last value would leave the field one line
 * off the ruler — which the other direction would then correct as a visible snap-back.
 */
internal suspend fun <T> collectUserScroll(
    isUserScrolling: () -> Boolean,
    value: () -> T,
    onValue: (T) -> Unit,
) {
    var touched = false
    snapshotFlow(isUserScrolling).collectLatest { scrolling ->
        if (scrolling) {
            touched = true
            snapshotFlow(value).collect(onValue)
        } else if (touched) {
            onValue(value())
        }
    }
}

/**
 * Drives a slider to whatever its value has become.
 *
 * `collectLatest` rather than `collect`: while the user drags one card the other two change every
 * frame, and each change has to cancel the animation in flight rather than queue behind it.
 */
private suspend fun <T> syncDown(
    value: () -> T,
    isUserScrolling: () -> Boolean,
    moveTo: suspend (T, Boolean) -> Unit,
) {
    var placed = false
    snapshotFlow(value).collectLatest { target ->
        if (isUserScrolling()) return@collectLatest
        try {
            // The first placement is the restored value arriving before anything is on screen, so
            // it jumps rather than animating a scroll the user never asked for.
            moveTo(target, placed)
            placed = true
        } catch (cancellation: CancellationException) {
            // A scroll that loses the mutex to the user's finger must not take the sync down with
            // it: a scroll animation started while a drag holds the scroll mutex at a higher
            // priority is cancelled outright, and a coroutine that ends that way ends quietly.
            currentCoroutineContext().ensureActive()
        }
    }
}

internal fun paceCalculatorStateSaver(
    distanceSliderState: DistanceSliderState,
    paceSliderState: PaceSliderState,
    timeSliderState: TimeSliderState,
): Saver<PaceCalculatorState, Any> = listSaver(
    save = { state ->
        listOf(
            state.distance.kilometers,
            // Milliseconds, not seconds: in miles the pace per kilometre isn't a whole number of
            // seconds, and rounding it on every rotation is the drift this all exists to avoid.
            state.pace.inWholeMilliseconds,
            state.time.inWholeMilliseconds,
            state.selectedMetric.name,
            state.selectedUnit.name,
        )
    },
    restore = { saved ->
        PaceCalculatorState(
            distanceSliderState,
            paceSliderState,
            timeSliderState,
            distance = Distance(saved[0] as Double),
            pace = (saved[1] as Long).milliseconds,
            time = (saved[2] as Long).milliseconds,
            // By name, and falling back rather than throwing, so a bundle written by an older
            // build with different entries degrades to a default.
            selectedMetric = Metric.entries.firstOrNull { it.name == saved[3] } ?: Metric.Pace,
            selectedUnit = DistanceUnit.entries.firstOrNull { it.name == saved[4] }
                ?: DistanceUnit.Kilometers,
        )
    },
)

@Composable
fun rememberPaceCalculatorState(): PaceCalculatorState {
    val distanceState = rememberDistanceSliderState()
    val paceState = rememberPaceSliderState()
    val timeState = rememberTimeSliderState()

    val saver = remember(distanceState, paceState, timeState) {
        paceCalculatorStateSaver(distanceState, paceState, timeState)
    }
    val state = rememberSaveable(distanceState, paceState, timeState, saver = saver) {
        PaceCalculatorState(distanceState, paceState, timeState)
    }

    LaunchedEffect(state) { state.sync() }

    return state
}

@Composable
fun UnitSelector(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        selected = state.selectedUnit,
        *DistanceUnit.entries.toTypedArray(),
        modifier = modifier,
        onSelect = { state.selectUnit(it) },
    )
}

@Composable
private fun MetricCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slider: @Composable () -> Unit,
) {
    Card(
        isSelected = selected,
        modifier = modifier,
        onClick = onClick,
    ) {
        Column {
            Heading3(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
            )
            slider()
        }
    }
}

/**
 * widthIn before fillMaxWidth: constraints flow outwards in, so the fill has to resolve against the
 * already capped maximum. The other order would make the cap a no-op.
 */
@Composable
private fun cardModifier(maxCardWidth: Dp): Modifier =
    Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
        .widthIn(max = maxCardWidth)
        .fillMaxWidth()

/**
 * The three metric cards, so a caller can drop them into a list of its own.
 *
 * @param maxCardWidth caps how wide a card grows on a roomy window; a card is centred within
 * whatever is left over. [Dp.Unspecified] lets them fill the list.
 */
fun LazyListScope.metricCardItems(
    state: PaceCalculatorState,
    maxCardWidth: Dp = Dp.Unspecified,
) {
    item("time") {
        MetricCard(
            title = "Time ${state.timeComparison.text} ${state.displayedTime}",
            selected = state.selectedMetric == Metric.Time,
            onClick = { state.selectMetric(Metric.Time) },
            modifier = cardModifier(maxCardWidth),
        ) {
            TimeSlider(
                state = state.timeSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Time
            )
        }
    }

    item("distance") {
        MetricCard(
            title = "Distance ${state.distanceComparison.text} ${state.displayedDistance}",
            selected = state.selectedMetric == Metric.Distance,
            onClick = { state.selectMetric(Metric.Distance) },
            modifier = cardModifier(maxCardWidth),
        ) {
            DistanceSlider(
                state = state.distanceSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Distance
            )
        }
    }

    item("pace") {
        MetricCard(
            title = "Pace ${state.paceComparison.text} ${state.displayedPace}",
            selected = state.selectedMetric == Metric.Pace,
            onClick = { state.selectMetric(Metric.Pace) },
            modifier = cardModifier(maxCardWidth),
        ) {
            PaceSlider(
                state = state.paceSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Pace
            )
        }
    }
}

/** The cards on their own, for a layout that places the unit selector somewhere else. */
@Composable
fun MetricCards(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = RisoTheme.dimens.screenPadding),
    maxCardWidth: Dp = Dp.Unspecified,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        metricCardItems(state, maxCardWidth)
    }
}

/** The unit selector and the cards in a single column. */
@Composable
fun PaceCalculator(
    modifier: Modifier = Modifier,
    state: PaceCalculatorState = rememberPaceCalculatorState(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = RisoTheme.dimens.screenPadding),
    maxCardWidth: Dp = Dp.Unspecified,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("unit") {
            Box(
                modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                UnitSelector(
                    state = state,
                    modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding),
                )
            }
        }
        metricCardItems(state, maxCardWidth)
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun PaceCalculatorPreview() {
    RisoTheme {
        PaceCalculator(Modifier.background(RisoTheme.colors.paper))
    }
}

/**
 * This number as two digits, zero-padded — `7` as `"07"`.
 *
 * The clock faces and the distance readout are all fixed-width, so a single-digit part has to carry
 * its own leading zero rather than let the text reflow around it. `String.format` would say the same
 * thing, but it is a JVM-only extension and this is read on iOS too.
 */
internal fun Int.twoDigits(): String = toString().padStart(2, '0')
