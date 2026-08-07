package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.components.Card
import kotlinx.coroutines.CoroutineScope
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
) {
    var selectedMetric by mutableStateOf(Metric.Pace)

    var selectedUnit by mutableStateOf(DistanceUnit.Kilometers)
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

@Composable
fun rememberPaceCalculatorState(): PaceCalculatorState {
    val distanceState = rememberDistanceSliderState()
    val paceState = rememberPaceSliderState()
    val timeState = rememberTimeSliderState()
    val coroutineScope = rememberCoroutineScope()

    return remember {
        PaceCalculatorState(
            distanceState,
            paceState,
            timeState,
            coroutineScope,
        )
    }
}

@Composable
fun PaceCalculator(
    modifier: Modifier = Modifier,
    state: PaceCalculatorState = rememberPaceCalculatorState(),
) {
    val distanceState = state.distanceSliderState
    val paceState = state.paceSliderState
    val timeState = state.timeSliderState

    LaunchedEffect(state) {
        snapshotFlow { state.computedDistance }
            .filterNotNull()
            .collectLatest { distanceState.animateToDistance(it) }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.computedPace }
            .filterNotNull()
            .collectLatest { paceState.animateToPace(it) }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.computedTime }
            .filterNotNull()
            .collectLatest { timeState.animateToTime(it) }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = RisoTheme.dimens.screenPadding)
    ) {
        item("unit") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ButtonGroup(
                    selected = state.selectedUnit,
                    *DistanceUnit.entries.toTypedArray(),
                    modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding),
                    onSelect = { state.selectUnit(it) },
                )
            }
        }
        item("time") {
            Card(
                selected = state.selectedMetric == Metric.Time,
                modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
                    .clickable(onClick = { state.selectedMetric = Metric.Time })
            ) {
                Column {
                    Heading3(
                        text = "Time = ${state.displayedTime}",
                        modifier = Modifier.padding(horizontal = 16.dp)
                            .padding(top = 16.dp)
                    )
                    TimeSlider(
                        state = timeState,
                        userScrollEnabled = state.selectedMetric != Metric.Time
                    )
                }
            }
        }

        item("distance") {
            Card(
                selected = state.selectedMetric == Metric.Distance,
                modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
                    .clickable(onClick = { state.selectedMetric = Metric.Distance })
            ) {
                Column {
                    Heading3(
                        text = "Distance = ${state.displayedDistance}",
                        modifier = Modifier.padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 16.dp)
                    )
                    DistanceSlider(
                        state = distanceState,
                        userScrollEnabled = state.selectedMetric != Metric.Distance
                    )
                }
            }
        }

        item("pace") {
            Card(
                selected = state.selectedMetric == Metric.Pace,
                modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
                    .clickable(onClick = { state.selectedMetric = Metric.Pace })
            ) {
                Column {
                    Heading3(
                        text = "Pace = ${state.displayedPace}",
                        modifier = Modifier.padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 16.dp)
                    )
                    PaceSlider(
                        state = paceState,
                        userScrollEnabled = state.selectedMetric != Metric.Pace
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PaceCalculatorPreview() {
    RisoTheme {
        PaceCalculator(Modifier.background(RisoTheme.colors.paper))
    }
}
