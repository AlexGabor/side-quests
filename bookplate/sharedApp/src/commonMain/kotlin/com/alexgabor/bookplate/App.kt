package com.alexgabor.bookplate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Bookplate has no links, so unlike Pacer there is no launch input: [RootNavigation] always starts
 * at Home, and `LocalDeepLink` is left at its empty default.
 *
 * @param showPlaying emits each time the platform asks to show the playing book, as tapping Android's media
 *   notification does. It carries nothing: Player opens on top for whatever book is loaded.
 */
@Composable
fun App(showPlaying: Flow<Unit> = emptyFlow()) {
    RisoTheme {
        RootNavigation(showPlaying)
    }
}

@Composable
@Preview
private fun AppPreview() {
    App()
}
