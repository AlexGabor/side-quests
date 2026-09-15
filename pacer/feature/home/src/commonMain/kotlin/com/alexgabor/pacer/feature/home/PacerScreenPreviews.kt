package com.alexgabor.pacer.feature.home

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.layout.PreviewWindowSizeEdges
import com.alexgabor.design.riso.layout.PreviewWindowSizes
import com.alexgabor.lib.appstateurl.fakeAppUrlModule
import org.koin.compose.KoinApplicationPreview

@PreviewWindowSizes
@Composable
private fun PacerScreenWindowSizesPreview() {
    KoinApplicationPreview(application = { modules(fakeAppUrlModule) }) {
        RisoTheme {
            PacerScreen(
                onSettingsClick = {},
                modifier = Modifier.background(RisoTheme.colors.paper),
            )
        }
    }
}

@PreviewWindowSizeEdges
@Composable
private fun PacerScreenWindowSizeEdgesPreview() {
    KoinApplicationPreview(application = { modules(fakeAppUrlModule) }) {
        RisoTheme {
            PacerScreen(
                onSettingsClick = {},
                modifier = Modifier.background(RisoTheme.colors.paper),
            )
        }
    }
}
