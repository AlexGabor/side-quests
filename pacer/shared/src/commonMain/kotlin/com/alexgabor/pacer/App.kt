package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.paper.paperTexture
import com.alexgabor.design.riso.print.risoPrint

@Composable
@Preview
fun App() {
    RisoTheme {
        PacerScreen(
            modifier = Modifier
                .risoPrint()
                .paperTexture()
        )
    }
}
