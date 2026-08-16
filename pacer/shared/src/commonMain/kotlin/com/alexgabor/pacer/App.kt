package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper

@Composable
@Preview
fun App() {
    RisoTheme {
        PacerScreen(modifier = Modifier.risoPaper().risoInk(RisoTheme.colors.content))
    }
}
