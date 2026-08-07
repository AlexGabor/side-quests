package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.components.Card
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

enum class Metric {
    Distance, Pace, Time
}

class PaceCalculatorState(
    val distanceSliderState: DistanceSliderState,
    val paceSliderState: PaceSliderState,
    val timeSliderState: TimeSliderState,
) {
    var selectedMetric by mutableStateOf(Metric.Pace)

    val computedDistance: Distance? by derivedStateOf {
        if (selectedMetric != Metric.Distance) return@derivedStateOf null
        val time = timeSliderState.selectedTime
        val pace = paceSliderState.selectedPace

        val totalSeconds = time.hours * 3600 + time.minutes * 60 + time.seconds
        val paceSecondsPerKilometer = pace.minutes * 60 + pace.seconds
        if (paceSecondsPerKilometer <= 0) return@derivedStateOf null

        val hundredths = totalSeconds * 100 / paceSecondsPerKilometer
        Distance(
            kilometers = hundredths / 100,
            fraction = hundredths % 100,
        )
    }

    val computedPace: Pace? by derivedStateOf {
        if (selectedMetric != Metric.Pace) return@derivedStateOf null
        val time = timeSliderState.selectedTime
        val distance = distanceSliderState.selectedDistance

        val totalSeconds = time.hours * 3600 + time.minutes * 60 + time.seconds
        val hundredths = distance.kilometers * 100 + distance.fraction
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

        val hundredths = distance.kilometers * 100 + distance.fraction
        val paceSecondsPerKilometer = pace.minutes * 60 + pace.seconds

        val totalSeconds = hundredths * paceSecondsPerKilometer / 100
        Time(
            hours = totalSeconds / 3600,
            minutes = (totalSeconds % 3600) / 60,
            seconds = totalSeconds % 60,
        )
    }

    val displayedDistance: String by derivedStateOf {
        val km = distanceSliderState.selectedDistance.kilometers
        val fraction = String.format("%02d", distanceSliderState.selectedDistance.fraction)
        "$km.$fraction km"
    }

    val displayedPace: String by derivedStateOf {
        val minutes = paceSliderState.selectedPace.minutes
        val seconds = String.format("%02d", paceSliderState.selectedPace.seconds)
        "$minutes:$seconds min/km"
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

    return remember {
        PaceCalculatorState(
            distanceState,
            paceState,
            timeState
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
