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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.LocalPress
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.attributes.RisoPress
import com.alexgabor.design.riso.risograph.inks.onRisoPaper
import com.alexgabor.design.riso.risograph.inks.risoFade
import com.alexgabor.design.riso.risograph.inks.risoFadeAsAlpha
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.inks.RisoFadeAlphaElement
import com.alexgabor.design.riso.risograph.inks.risoKnockout
import com.alexgabor.design.riso.risograph.inks.risoOverprint
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

    /**
     * A frisket: one hole on the sheet, whatever the drums did.
     *
     * The two inks sit in slots 0 and 7, which land about 5 dp apart, so a hole that rode its drum
     * would cut in two places rather than one. The band between them would come back inked by
     * whichever drum did not have its hole there — single-drum colour where there should be stock —
     * which is why the samples just inside the hole's edges matter more than the one at its centre.
     */
    @Test
    fun aKnockoutCutsOneHoleForEveryDrum() {
        val first = RisoColors.inks.fluorescentPink
        val second = RisoColors.inks.blue
        val solid = risoOverprint(RisoColors.paper, listOf(first to 1f, second to 1f))
        val pixels = render {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(96.dp).risoInk(first, second).background(solid),
                    contentAlignment = Alignment.Center,
                ) {
                    // The hole is cut from the knockout's own artwork, so it needs some: a solid
                    // fill stands in for the glyphs of reversed-out type. Its colour is never seen.
                    Box(Modifier.size(48.dp).risoKnockout().background(Color.Black))
                }
            }
        }
        // The hole spans 36..84 dp. Bare stock throughout, right up to its edges.
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, pixels.at(38, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, pixels.at(82, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, 38), levels = 1)
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, 82), levels = 1)
        // And the artwork around it still prints, so the hole is a hole and not a blank pass.
        assertInked(pixels.at(30, sizeDp / 2))
        assertInked(pixels.at(90, sizeDp / 2))
        assertInked(pixels.at(sizeDp / 2, 30))
        assertInked(pixels.at(sizeDp / 2, 90))
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


    // --- Running the press lighter ---------------------------------------------------------

    /**
     * A fade of one is the press as loaded, down to the pixel — the fast path that keeps every
     * unfaded screen exactly as it printed before there was such a thing as a fade.
     */
    @Test
    fun aFadeOfOnePrintsWhatNoFadeDoes() {
        val ink = RisoColors.inks.blue
        val faded = render { Box(Modifier.fillMaxSize().risoFade(1f)) { inkedSquare(ink) } }
        val plain = render { Box(Modifier.fillMaxSize()) { inkedSquare(ink) } }
        assertTrue(faded.bytes.contentEquals(plain.bytes), "a fade of one changed the print")
    }

    @Test
    fun aFadeOfZeroLaysDownNoInk() {
        val ink = RisoColors.inks.blue
        val pixels = render { Box(Modifier.fillMaxSize().risoFade(0f)) { inkedSquare(ink) } }
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2 - 20, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, pixels.at(sizeDp / 2, sizeDp / 2 + 20), levels = 1)
    }

    /**
     * The whole point of the thing: half the ink is smaller dots of the same ink, not the same dots
     * in a paler one. So the darkest pixel in the region is still solid ink, the lightest is still
     * bare stock, and only the average moves.
     */
    @Test
    fun aHalfFadePrintsSmallerDotsAtFullStrength() {
        val ink = RisoColors.inks.blue
        val full = render { Box(Modifier.fillMaxSize()) { inkedSquare(ink) } }
        val half = render { Box(Modifier.fillMaxSize().risoFade(0.5f)) { inkedSquare(ink) } }

        assertTrue(
            half.meanInk() < full.meanInk() * 0.8f,
            "half the ink was not lighter: ${half.meanInk()} against ${full.meanInk()}",
        )
        assertClose(ink.onRisoPaper(), half.darkest(), levels = 3)
        assertClose(RisoColors.paper, half.lightest(), levels = 2)
    }

    /**
     * Two lighter runs of the press, not the lighter of the two: a half inside a half reaches the
     * sheet as a quarter, pixel for pixel.
     */
    @Test
    fun nestedFadesMultiply() {
        val ink = RisoColors.inks.blue
        val nested = render {
            Box(Modifier.fillMaxSize().risoFade(0.5f)) {
                Box(Modifier.fillMaxSize().risoFade(0.5f)) { inkedSquare(ink) }
            }
        }
        val quarter = render { Box(Modifier.fillMaxSize().risoFade(0.25f)) { inkedSquare(ink) } }
        assertTrue(nested.bytes.contentEquals(quarter.bytes), "nested fades did not multiply")
    }

    /**
     * A frisket is not ink. However light the press is running, the paper it holds off the sheet is
     * the same paper — so the hole stays a hole while the ink around it thins.
     */
    @Test
    fun aKnockoutStaysFullyCutWhileTheInkThins() {
        val ink = RisoColors.inks.fluorescentPink
        val content = @Composable {
            Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(96.dp).risoInk(ink, offsetScale = 0f).background(ink.onRisoPaper()),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(48.dp).risoKnockout().background(Color.Black))
                }
            }
        }
        val full = render { content() }
        val half = render { Box(Modifier.fillMaxSize().risoFade(0.5f)) { content() } }

        // The hole reads as bare stock at either fade, not as stock with half a pass over it.
        assertClose(RisoColors.paper, half.at(sizeDp / 2, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, half.at(38, sizeDp / 2), levels = 1)
        assertClose(RisoColors.paper, half.at(sizeDp / 2, 82), levels = 1)
        // And the ink around it is thinner than it was.
        assertInked(full.at(30, sizeDp / 2))
        assertTrue(
            half.meanInk(from = 16, to = 32) < full.meanInk(from = 16, to = 32) * 0.8f,
            "the ink around the hole did not thin",
        )
    }

    @Test
    fun contentWithoutInkIsUntouchedByAFade() {
        val plain = Color(0xFF3A7BD5)
        val pixels = render {
            Box(Modifier.fillMaxSize().risoFade(0.5f).risoPaper(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).background(plain))
            }
        }
        assertClose(plain, pixels.at(sizeDp / 2, sizeDp / 2), levels = 0)
    }

    @Test
    fun contentWithoutInkTakesTheFadeWhenItAsks() {
        val plain = Color(0xFF3A7BD5)
        val pixels = render {
            Box(Modifier.fillMaxSize().risoFade(0.5f).risoPaper(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).risoFadeAsAlpha().background(plain))
            }
        }
        val over = { a: Float, b: Float -> a * 0.5f + b * 0.5f }
        val expected = Color(
            red = over(plain.red, RisoColors.paper.red),
            green = over(plain.green, RisoColors.paper.green),
            blue = over(plain.blue, RisoColors.paper.blue),
        )
        assertClose(expected, pixels.at(sizeDp / 2, sizeDp / 2), levels = 2)
    }

    @Test
    fun withEffectsOffAFadeIsAPlainAlphaAndIsTakenOnce() {
        val plain = Color(0xFF3A7BD5)
        var asAlpha: Modifier = Modifier
        val pixels = render(effectsEnabled = false) {
            // The stock stays outside the fade, so what is faded lands on it rather than on nothing.
            Box(Modifier.fillMaxSize().background(RisoColors.paper)) {
                Box(Modifier.fillMaxSize().risoFade(0.5f), contentAlignment = Alignment.Center) {
                    asAlpha = Modifier.risoFadeAsAlpha()
                    Box(Modifier.size(64.dp).then(asAlpha).background(plain))
                }
            }
        }
        // Nothing of its own to add: the fade is already a plain alpha over everything inside it.
        assertFalse(
            asAlpha.any { it is RisoFadeAlphaElement },
            "risoFadeAsAlpha faded the content a second time with effects off",
        )
        val over = { a: Float, b: Float -> a * 0.5f + b * 0.5f }
        val expected = Color(
            red = over(plain.red, RisoColors.paper.red),
            green = over(plain.green, RisoColors.paper.green),
            blue = over(plain.blue, RisoColors.paper.blue),
        )
        // Two levels for the rounding in the composite. Taking the fade twice would be sixty.
        assertClose(expected, pixels.at(sizeDp / 2, sizeDp / 2), levels = 2)
    }

    /** A square of solid ink, centred, printed in register so the samples land where they read. */
    @Composable
    private fun inkedSquare(ink: Color) {
        Box(Modifier.fillMaxSize().risoPaper(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp)
                    .risoInk(ink, offsetScale = 0f)
                    .background(ink.onRisoPaper())
            )
        }
    }


    /**
     * How far the inked region sits off bare stock on average, over a band around the centre.
     *
     * An average rather than a pixel, because thinning the ink moves the dots rather than the ink:
     * a single pixel is either inside a dot or between two, at any fade.
     */
    private fun Pixels.meanInk(from: Int = 0, to: Int = 24): Float {
        var total = 0f
        var count = 0
        for (y in -to..to) for (x in -to..to) {
            if (maxOf(abs(x), abs(y)) < from) continue
            val c = at(sizeDp / 2 + x, sizeDp / 2 + y)
            total += RisoColors.paper.luminance() - c.luminance()
            count++
        }
        return total / count
    }

    /** The most ink laid down anywhere in the inked region: the middle of a dot. */
    private fun Pixels.darkest(to: Int = 24): Color =
        region(to).minBy { it.luminance() }

    /** The least: bare stock between the dots. */
    private fun Pixels.lightest(to: Int = 24): Color =
        region(to).maxBy { it.luminance() }

    private fun Pixels.region(to: Int): List<Color> =
        (-to..to).flatMap { y -> (-to..to).map { x -> at(sizeDp / 2 + x, sizeDp / 2 + y) } }

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

    /** Ink was laid here: far enough off the bare stock that no amount of grain explains it. */
    private fun assertInked(actual: Color) {
        val off = listOf(
            RisoColors.paper.red - actual.red,
            RisoColors.paper.green - actual.green,
            RisoColors.paper.blue - actual.blue,
        ).maxOf { abs(it) * 255f }
        assertTrue(off > 16f, "expected ink, got $actual (only $off levels off the stock)")
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
