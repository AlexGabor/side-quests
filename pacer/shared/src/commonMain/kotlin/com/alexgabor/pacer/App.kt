package com.alexgabor.pacer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.pass.risoInk
import com.alexgabor.design.riso.print.risoPaper

@Composable
@Preview
fun App() {
    RisoTheme {
        // A one-colour print: the sheet, and the black drum over all of it. Components that name
        // their own inks — cards, buttons, the unit picker — nest inside and print on theirs
        // instead, so the accent stays an accent.
        PacerScreen(modifier = Modifier.risoPaper().risoInk(RisoTheme.colors.content))
    }
}
