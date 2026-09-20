package com.alexgabor.design.riso.risograph.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.LocalPress
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.attributes.RisoPress
import com.alexgabor.design.riso.risograph.inks.onRisoPaper
import com.alexgabor.design.riso.risograph.inks.risoInk
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sheet and the ink, rendered for real through skia, checked against the behaviour the paper
 * promises. The press's texture — mottle, grain, ink gain — is switched off wherever an exact color
 * is asserted, since its whole job is to make ink uneven.
 */
class SheetRenderTest {

    private val density = 2f
    private val sizeDp = 120
    private val pink = Color(0xFFF6D9E1)
    private val flatPress = RisoPress.copy(mottle = 0f, grain = 0f, spread = 0f)

    @Test
    fun solidInkOnTheDefaultStockPrintsAsAuthored() {
        val ink = RisoColors.inks.fluorescentPink
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).risoInk(ink, offsetScale = 0f).background(ink.onRisoPaper()))
            }
        }
        assertClose(ink.onRisoPaper(), pixels.at(sizeDp / 2, sizeDp / 2), levels = 2)
    }

    @Test
    fun bareStockAroundTheInkIsUntouched() {
        val ink = RisoColors.inks.blue
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).risoInk(ink).background(ink.onRisoPaper()))
            }
        }
        // Just outside the artwork and its registration error: the stock as it is, with no halo.
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, 12), levels = 1)
        assertClose(RisoColors.paper, pixels.at(12, sizeDp / 2), levels = 1)
    }

    /**
     * A pass that lands in a layer of its own instead of on the stock.
     *
     * This is what a scroll container does for as long as its overscroll stretch is live: the
     * content is recorded into a fresh render node, which starts empty, and the sheet stays outside
     * it. A pass composited onto nothing must still come off as ink and bare paper, not as a fill.
     */
    @Test
    fun inkInsideAnIsolatedLayerStillLeavesBarePaper() {
        val ink = RisoColors.inks.vintageBlack
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(96.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .risoInk(ink, offsetScale = 0f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(32.dp).background(ink.onRisoPaper()))
                }
            }
        }
        // Inside the pass, well away from the artwork: the stock, not the white a drum hands back
        // where it laid no ink.
        assertClose(RisoColors.paper, pixels.at(20, 20), levels = 1)
        assertClose(RisoColors.paper, pixels.at(sizeDp - 20, 20), levels = 1)
        // And the artwork still prints. Straight onto the stock the composite is exact; through a
        // layer it is rebuilt from a premultiplied pass, which cannot be exact for an ink that is
        // not neutral — it comes out light by (1 - stock) * (transmittance - its own minimum), so
        // about a level for this ink and around twenty on the strongest channel of a saturated one.
        assertClose(ink.onRisoPaper(), pixels.at(sizeDp / 2, sizeDp / 2), levels = 2)
    }

    @Test
    fun artworkInTheSheetsOwnColorPrintsNoInk() {
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(RisoPaper(colorFront = pink, colorBack = pink))) {
                Box(Modifier.fillMaxSize().padding(16.dp).risoInk(RisoColors.content).background(pink))
            }
        }
        assertClose(pink, pixels.at(sizeDp / 2, sizeDp / 2), levels = 1)
    }

    @Test
    fun contentWithoutInkIsDrawnAsAuthored() {
        val plain = Color(0xFF3A7BD5)
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).background(plain))
            }
        }
        assertClose(plain, pixels.at(sizeDp / 2, sizeDp / 2), levels = 0)
    }

    @Test
    fun anEmptySheetStillShowsItsStock() {
        val pixels = render { Box(Modifier.fillMaxSize().risoPaper(RisoPaper(colorFront = pink, colorBack = pink))) }
        assertClose(pink, pixels.at(sizeDp / 2, sizeDp / 2), levels = 1)
    }

    /**
     * A sheet laid out larger shows more of the same surface rather than a stretched one: over the
     * part two sizes share, their pixels agree exactly — nothing was regenerated for the new size.
     * The stock has a darker back, so the surface's shading actually shows.
     */
    @Test
    fun resizingRevealsMoreOfTheSameSurface() {
        val textured = RisoPaper(colorBack = Color(0xFFB9AF9C), contrast = 0.4f)
        val small = render(sizeDp = 80) { Box(Modifier.fillMaxSize().risoPaper(textured)) }
        val large = render(sizeDp = 120) { Box(Modifier.fillMaxSize().risoPaper(textured)) }
        val distinct = mutableSetOf<Int>()
        for (y in 0 until 80) for (x in 0 until 80) {
            val a = small.at(x, y)
            assertClose(a, large.at(x, y), levels = 0)
            distinct += (a.red * 255).roundToInt()
        }
        assertTrue(distinct.size > 3, "the surface did not show: only ${distinct.size} shades")
    }

    @Test
    fun withEffectsOffThereIsNoSheet() {
        var modifier: Modifier = Modifier
        render(effectsEnabled = false) {
            modifier = Modifier.risoPaper()
            Box(modifier.fillMaxSize())
        }
        assertFalse(modifier.any { it is RisoSheetElement }, "a sheet was made with effects off")
    }

    private class Pixels(val width: Int, val bytes: ByteArray, val density: Float) {
        /** The pixel at ([xDp], [yDp]), as a color. */
        fun at(xDp: Int, yDp: Int): Color {
            val x = (xDp * density).roundToInt()
            val y = (yDp * density).roundToInt()
            val i = (y * width + x) * 4
            return Color(
                red = (bytes[i].toInt() and 0xFF) / 255f,
                green = (bytes[i + 1].toInt() and 0xFF) / 255f,
                blue = (bytes[i + 2].toInt() and 0xFF) / 255f,
            )
        }
    }

    private fun assertClose(expected: Color, actual: Color, levels: Int) {
        val worst = listOf(
            expected.red - actual.red,
            expected.green - actual.green,
            expected.blue - actual.blue,
        ).maxOf { abs(it) * 255f }
        assertTrue(worst <= levels + 0.5f, "expected $expected, got $actual ($worst levels off)")
    }

    private fun render(
        effectsEnabled: Boolean = true,
        sizeDp: Int = this.sizeDp,
        content: @Composable () -> Unit,
    ): Pixels {
        val pixels = (sizeDp * density).toInt()
        return ImageComposeScene(pixels, pixels, Density(density)) {
            RisoTheme(effectsEnabled = effectsEnabled) {
                CompositionLocalProvider(LocalPress provides flatPress) { content() }
            }
        }.use { scene ->
            // The sheet's tiles load off the frame; render until the picture stops changing.
            var previous = scene.render().pixels()
            val deadline = System.currentTimeMillis() + 20_000
            var stable = 0
            while (stable < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
                val next = scene.render().pixels()
                stable = if (next.contentEquals(previous)) stable + 1 else 0
                previous = next
            }
            Pixels(pixels, previous, density)
        }
    }

    private fun Image.pixels(): ByteArray =
        Bitmap.makeFromImage(this).readPixels(
            ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
            dstRowBytes = width * 4,
            srcX = 0,
            srcY = 0,
        )!!
}
