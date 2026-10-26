package com.alexgabor.bookplate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme

@Composable
fun App() {
    RisoTheme {
        RootNavigation()
    }
}

@Composable
@Preview
private fun AppPreview() {
    App()
}
