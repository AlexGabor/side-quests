package com.alexgabor.pacer

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.paper.paperTexture
import com.alexgabor.design.riso.print.risoPrint
import com.alexgabor.pacer.slider.PaceCalculator

@Composable
@Preview
fun App() {
    RisoTheme {
        PaceCalculator(
            modifier = Modifier.safeDrawingPadding()
                .paperTexture()
                .risoPrint())
    }
}