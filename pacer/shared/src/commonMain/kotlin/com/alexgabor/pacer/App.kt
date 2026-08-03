package com.alexgabor.pacer

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.pacer.slider.PaceCalculator

@Composable
@Preview
fun App() {
    MaterialTheme {
        PaceCalculator(Modifier.safeDrawingPadding())
    }
}