package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Sliders(
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color.White)) {
        val distanceState = rememberDistanceSliderState()
        Text("Distance ${distanceState.selectedDistance.kilometers}.${distanceState.selectedDistance.fraction}")
        DistanceSlider(
            state = distanceState
        )

        val paceState = rememberPaceSliderState()
        Text("Pace ${paceState.selectedPace.minutes}:${paceState.selectedPace.seconds}")
        PaceSlider(
            state = paceState
        )

        val timeState = rememberTimeSliderState()
        Text("Time ${timeState.selectedTime.hours}:${timeState.selectedTime.minutes}:${timeState.selectedTime.seconds}")
        TimeSlider(
            state = timeState
        )
    }
}

@Preview
@Composable
private fun SlidersPreview() {
    Sliders()
}
