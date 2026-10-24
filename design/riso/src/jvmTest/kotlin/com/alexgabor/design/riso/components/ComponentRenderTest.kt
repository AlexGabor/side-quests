package com.alexgabor.design.riso.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.RisoColors
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The new components drawn for real through skia, with the print effects off so the flat colors
 * they promise can be read straight off the pixels.
 */
class ComponentRenderTest {

    private val density = 2f
    private val widthDp = 100
    private val heightDp = 20
    private val accent = RisoColors.accent
    private val remaining = RisoColors.content.copy(alpha = 0.5f).compositeOver(RisoColors.paper)

    @Test
    fun noProgressIsAllRemaining() {
        val line = progressLine(0f)
        assertClose(remaining, line.at(1))
        assertClose(remaining, line.at(50))
        assertClose(remaining, line.at(99))
    }

    @Test
    fun partialProgressSplitsAtTheValue() {
        val line = progressLine(0.4f)
        assertClose(accent, line.at(1))
        assertClose(accent, line.at(38))
        assertClose(remaining, line.at(42))
        assertClose(remaining, line.at(99))
    }

    @Test
    fun completeProgressIsAllAccent() {
        val line = progressLine(1f)
        assertClose(accent, line.at(1))
        assertClose(accent, line.at(50))
        assertClose(accent, line.at(99))
    }

    @Test
    fun rightToLeftFillsFromTheRight() {
        val line = progressLine(0.4f, LayoutDirection.Rtl)
        assertClose(remaining, line.at(1))
        assertClose(remaining, line.at(58))
        assertClose(accent, line.at(62))
        assertClose(accent, line.at(99))
    }

    @Test
    fun everyIconDrawsInsideItsBox() {
        IconType.entries.forEach { type ->
            val pixels = render(sizeDp = 32, heightDp = 32) {
                Icon(type = type)
            }
            val inked = (0 until pixels.width).sumOf { x ->
                (0 until pixels.height).count { y -> pixels.color(x, y) != RisoColors.paper.toArgb() }
            }
            assertTrue(inked > 0, "$type drew nothing")
        }
    }

    @Test
    fun withNoSheetOpenTheBackdropLeavesThePageAlone() {
        val pixels = render { Box(Modifier.fillMaxSize().background(RisoColors.content)) }
        assertClose(RisoColors.content, Color(pixels.color(pixels.width / 2, pixels.height / 2)))
    }

    @Test
    fun underAnOpenSheetThePageIsCoveredByPaperWithEffectsOff() {
        val pixels = render {
            Box(Modifier.fillMaxSize().background(RisoColors.content))
            // What an open sheet does: tell the page under it that it is all the way up.
            val backdrop = LocalRisoBackdrop.current!!
            SideEffect { backdrop.report(sheet = "sheet", shown = 1f) }
        }
        val veiled = RisoColors.paper.copy(alpha = 1f - BottomSheetDefaults.BackdropFade)
            .compositeOver(RisoColors.content)
        assertClose(veiled, Color(pixels.color(pixels.width / 2, pixels.height / 2)))
    }

    /** One row of pixels through the middle of the line, in dp along it. */
    private fun progressLine(progress: Float, direction: LayoutDirection = LayoutDirection.Ltr): (Int) -> Color {
        val pixels = render {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    SmallProgress(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val y = pixels.height / 2
        return { xDp -> Color(pixels.color((xDp * density).roundToInt(), y)) }
    }

    private fun ((Int) -> Color).at(xDp: Int) = this(xDp)

    private fun render(
        sizeDp: Int = widthDp,
        heightDp: Int = this.heightDp,
        content: @Composable () -> Unit,
    ): Pixels {
        val width = (sizeDp * density).toInt()
        val height = (heightDp * density).toInt()
        return ImageComposeScene(width, height, Density(density)) {
            RisoTheme(effectsEnabled = false) {
                Box(
                    Modifier.fillMaxSize().background(RisoColors.paper),
                    contentAlignment = Alignment.Center,
                ) { content() }
            }
        }.use { scene ->
            val image = scene.render()
            val bytes = Bitmap.makeFromImage(image).readPixels(
                ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
                dstRowBytes = width * 4,
                srcX = 0,
                srcY = 0,
            )!!
            Pixels(width, height, bytes)
        }
    }

    private class Pixels(val width: Int, val height: Int, private val bytes: ByteArray) {
        fun color(x: Int, y: Int): Int {
            val i = (y * width + x) * 4
            fun c(o: Int) = bytes[i + o].toInt() and 0xFF
            return (c(3) shl 24) or (c(0) shl 16) or (c(1) shl 8) or c(2)
        }
    }

    private fun assertClose(expected: Color, actual: Color, levels: Int = 2) {
        val channels = listOf(
            expected.red to actual.red,
            expected.green to actual.green,
            expected.blue to actual.blue,
        )
        assertTrue(
            channels.all { (e, a) -> abs(e - a) * 255 <= levels },
            "expected $expected, got $actual",
        )
    }
}
