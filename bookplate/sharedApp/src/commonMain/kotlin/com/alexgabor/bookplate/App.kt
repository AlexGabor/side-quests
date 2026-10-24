package com.alexgabor.bookplate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme

/**
 * Bookplate has no links, so unlike Pacer there is no launch input: [RootNavigation] always starts
 * at Home, and `LocalDeepLink` is left at its empty default.
 */
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
