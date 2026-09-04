package com.alexgabor.pacer

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme

/**
 * The window shapes the screen has to hold up in. `widthDp`/`heightDp` set the constraints the
 * screen measures against, which is exactly what its `BoxWithConstraints` reads — so what renders
 * here is what a window of that size renders.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone portrait 411x891", widthDp = 411, heightDp = 891)
@Preview(name = "Phone portrait small 360x640", widthDp = 360, heightDp = 640)
@Preview(name = "Phone landscape 891x411", widthDp = 891, heightDp = 411)
@Preview(name = "Short wide 640x360", widthDp = 640, heightDp = 360)
@Preview(name = "Foldable unfolded 673x841", widthDp = 673, heightDp = 841)
@Preview(name = "Foldable landscape 841x673", widthDp = 841, heightDp = 673)
@Preview(name = "Tablet portrait 800x1280", widthDp = 800, heightDp = 1280)
@Preview(name = "Tablet landscape 1280x800", widthDp = 1280, heightDp = 800)
@Preview(name = "Desktop 1920x1080", widthDp = 1920, heightDp = 1080)
annotation class PreviewWindowSizes

/** The sizes either side of a breakpoint, plus the ones that squeeze a track hardest. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Tiny window 400x400", widthDp = 400, heightDp = 400)
@Preview(name = "Below medium width 599x479", widthDp = 599, heightDp = 479)
@Preview(name = "At medium width 600x479", widthDp = 600, heightDp = 479)
@Preview(name = "At medium height 600x480", widthDp = 600, heightDp = 480)
@Preview(name = "Ultra wide short 2000x400", widthDp = 2000, heightDp = 400)
annotation class PreviewWindowSizeEdges

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
