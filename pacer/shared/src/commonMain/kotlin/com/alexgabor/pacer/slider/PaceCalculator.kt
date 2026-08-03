package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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

    Column(modifier.background(Color.White)) {
        Row {
            Checkbox(
                checked = state.selectedMetric == Metric.Distance,
                onCheckedChange = { state.selectedMetric = Metric.Distance }
            )
            Text("Distance ${distanceState.selectedDistance.kilometers}.${distanceState.selectedDistance.fraction}")
        }
        DistanceSlider(
            state = distanceState
        )

        Row {
            Checkbox(
                checked = state.selectedMetric == Metric.Pace,
                onCheckedChange = { state.selectedMetric = Metric.Pace }
            )
            Text("Pace ${paceState.selectedPace.minutes}:${paceState.selectedPace.seconds}")
        }
        PaceSlider(
            state = paceState
        )

        Row {
            Checkbox(
                checked = state.selectedMetric == Metric.Time,
                onCheckedChange = { state.selectedMetric = Metric.Time }
            )
            Text("Time ${timeState.selectedTime.hours}:${timeState.selectedTime.minutes}:${timeState.selectedTime.seconds}")
        }
        TimeSlider(
            state = timeState
        )
    }
}

@Preview
@Composable
private fun PaceCalculatorPreview() {
    PaceCalculator()
}
