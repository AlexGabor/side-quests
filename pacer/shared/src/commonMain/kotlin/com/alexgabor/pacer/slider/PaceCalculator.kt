package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.components.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Metric {
    Distance, Pace, Time
}

enum class DistanceUnit(override val text: String) : ButtonGroupItem {
    Kilometers("km"),
    Miles("mi");

    val paceText: String get() = "min/$text"
}

private const val KILOMETERS_PER_MILE = 1.609344

class PaceCalculatorState(
    val distanceSliderState: DistanceSliderState,
    val paceSliderState: PaceSliderState,
    val timeSliderState: TimeSliderState,
    val coroutineScope: CoroutineScope,
    selectedMetricState: MutableState<Metric> = mutableStateOf(Metric.Pace),
    selectedUnitState: MutableState<DistanceUnit> = mutableStateOf(DistanceUnit.Kilometers),
) {
    var selectedMetric by selectedMetricState

    var selectedUnit by selectedUnitState
        private set

    val computedDistance: Distance? by derivedStateOf {
        if (selectedMetric != Metric.Distance) return@derivedStateOf null
        val time = timeSliderState.selectedTime
        val pace = paceSliderState.selectedPace

        val totalSeconds = time.hours * 3600 + time.minutes * 60 + time.seconds
        val paceSecondsPerKilometer = pace.minutes * 60 + pace.seconds
        if (paceSecondsPerKilometer <= 0) return@derivedStateOf null

        val hundredths = totalSeconds * 100 / paceSecondsPerKilometer
        Distance(
            whole = hundredths / 100,
            fraction = hundredths % 100,
        )
    }

    val computedPace: Pace? by derivedStateOf {
        if (selectedMetric != Metric.Pace) return@derivedStateOf null
        val time = timeSliderState.selectedTime
        val distance = distanceSliderState.selectedDistance

        val totalSeconds = time.hours * 3600 + time.minutes * 60 + time.seconds
        val hundredths = distance.whole * 100 + distance.fraction
        if (hundredths <= 0) return@derivedStateOf null

        val paceSecondsPerKilometer = totalSeconds * 100 / hundredths
        Pace(
            minutes = paceSecondsPerKilometer / 60,
            seconds = paceSecondsPerKilometer % 60,
        )
    }

    val computedTime: Time? by derivedStateOf {
        if (selectedMetric != Metric.Time) return@derivedStateOf null
        val distance = distanceSliderState.selectedDistance
        val pace = paceSliderState.selectedPace

        val hundredths = distance.whole * 100 + distance.fraction
        val paceSecondsPerKilometer = pace.minutes * 60 + pace.seconds

        val totalSeconds = hundredths * paceSecondsPerKilometer / 100
        Time(
            hours = totalSeconds / 3600,
            minutes = (totalSeconds % 3600) / 60,
            seconds = totalSeconds % 60,
        )
    }

    val displayedDistance: String by derivedStateOf {
        val whole = distanceSliderState.selectedDistance.whole
        val fraction = String.format("%02d", distanceSliderState.selectedDistance.fraction)
        "$whole.$fraction ${selectedUnit.text}"
    }

    val displayedPace: String by derivedStateOf {
        val minutes = paceSliderState.selectedPace.minutes
        val seconds = String.format("%02d", paceSliderState.selectedPace.seconds)
        "$minutes:$seconds ${selectedUnit.paceText}"
    }

    /**
     * Distance and pace are only ever "per unit" — the sliders carry no unit of their own — so a
     * change of unit is a change of the numbers on them, not just of the labels. Converting both
     * leaves the time, and therefore the run being described, untouched.
     */
    fun selectUnit(unit: DistanceUnit) {
        if (unit == selectedUnit) return
        val toKilometers = unit == DistanceUnit.Kilometers

        val distance = distanceSliderState.selectedDistance
        val hundredths = distance.whole * 100 + distance.fraction
        val converted = if (toKilometers) {
            (hundredths * KILOMETERS_PER_MILE).roundToInt()
        } else {
            (hundredths / KILOMETERS_PER_MILE).roundToInt()
        }

        val pace = paceSliderState.selectedPace
        val paceSeconds = pace.minutes * 60 + pace.seconds
        val convertedPaceSeconds = if (toKilometers) {
            (paceSeconds / KILOMETERS_PER_MILE).roundToInt()
        } else {
            (paceSeconds * KILOMETERS_PER_MILE).roundToInt()
        }

        selectedUnit = unit
        coroutineScope.launch {
            distanceSliderState.animateToDistance(
                Distance(
                    whole = converted / 100,
                    fraction = converted % 100,
                )
            )
        }
        coroutineScope.launch {
            paceSliderState.animateToPace(
                Pace(
                    minutes = convertedPaceSeconds / 60,
                    seconds = convertedPaceSeconds % 60,
                )
            )
        }
    }

    val displayedTime: String by derivedStateOf {
        val minutes = String.format("%02d", timeSliderState.selectedTime.minutes)
        val seconds = String.format("%02d", timeSliderState.selectedTime.seconds)
        "${timeSliderState.selectedTime.hours}h ${minutes}m ${seconds}s"
    }
}

/**
 * Drives the metric that isn't being edited towards whatever the other two describe. Lives with the
 * state rather than with the layout so that reflowing the screen — rotating into two panes, say —
 * doesn't tear the sync down and re-animate all three sliders.
 */
private suspend fun PaceCalculatorState.syncSliders() = coroutineScope {
    launch {
        snapshotFlow { computedDistance }
            .filterNotNull()
            .collectLatest { distanceSliderState.animateToDistance(it) }
    }

    launch {
        snapshotFlow { computedPace }
            .filterNotNull()
            .collectLatest { paceSliderState.animateToPace(it) }
    }

    launch {
        snapshotFlow { computedTime }
            .filterNotNull()
            .collectLatest { timeSliderState.animateToTime(it) }
    }
}

@Composable
fun rememberPaceCalculatorState(): PaceCalculatorState {
    val distanceState = rememberDistanceSliderState()
    val paceState = rememberPaceSliderState()
    val timeState = rememberTimeSliderState()
    val coroutineScope = rememberCoroutineScope()

    val selectedMetric = rememberSaveable {
        mutableStateOf(Metric.Pace)
    }
    val selectedUnit = rememberSaveable {
        mutableStateOf(DistanceUnit.Kilometers)
    }

    val state = remember {
        PaceCalculatorState(
            distanceState,
            paceState,
            timeState,
            coroutineScope,
            selectedMetric,
            selectedUnit,
        )
    }

    LaunchedEffect(state) { state.syncSliders() }

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
        selected = selected,
        modifier = modifier.clickable(onClick = onClick),
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
            title = "Time = ${state.displayedTime}",
            selected = state.selectedMetric == Metric.Time,
            onClick = { state.selectedMetric = Metric.Time },
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
            title = "Distance = ${state.displayedDistance}",
            selected = state.selectedMetric == Metric.Distance,
            onClick = { state.selectedMetric = Metric.Distance },
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
            title = "Pace = ${state.displayedPace}",
            selected = state.selectedMetric == Metric.Pace,
            onClick = { state.selectedMetric = Metric.Pace },
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
