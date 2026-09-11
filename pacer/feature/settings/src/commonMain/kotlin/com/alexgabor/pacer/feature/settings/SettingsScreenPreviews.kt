package com.alexgabor.pacer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.layout.PreviewWindowSizes
import com.alexgabor.pacer.core.settings.fakeSettingsModule
import org.koin.compose.KoinApplicationPreview

@PreviewWindowSizes
@Composable
private fun SettingsScreenWindowSizesPreview() {
    KoinApplicationPreview(application = { modules(fakeSettingsModule) }) {
        RisoTheme {
            SettingsScreen(
                onBackClick = {},
                modifier = Modifier.background(RisoTheme.colors.paper),
            )
        }
    }
}
