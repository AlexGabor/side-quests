package com.alexgabor.pacer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.layout.PreviewWindowSizes

@PreviewWindowSizes
@Composable
private fun SettingsScreenWindowSizesPreview() {
    RisoTheme {
        SettingsScreen(
            onBackClick = {},
            modifier = Modifier.background(RisoTheme.colors.paper),
        )
    }
}
