package com.alexgabor.stamp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.paper.risoPaper

/**
 * The sheet is on the root, but the ink is not: the components below load their own drums, and the
 * stage must not be inked by anything above it or the screen would stop matching what it exports.
 */
@Composable
fun App(exporter: IconExporter) {
    RisoTheme {
        StampScreen(exporter, modifier = Modifier.risoPaper())
    }
}
