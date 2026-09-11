package com.alexgabor.pacer.feature.home

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.layout.PreviewWindowSizeEdges
import com.alexgabor.design.riso.layout.PreviewWindowSizes

@PreviewWindowSizes
@Composable
private fun PacerScreenWindowSizesPreview() {
    RisoTheme {
        PacerScreen(
            onSettingsClick = {},
            modifier = Modifier.background(RisoTheme.colors.paper),
        )
    }
}

@PreviewWindowSizeEdges
@Composable
private fun PacerScreenWindowSizeEdgesPreview() {
    RisoTheme {
        PacerScreen(
            onSettingsClick = {},
            modifier = Modifier.background(RisoTheme.colors.paper),
        )
    }
}
