package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.risograph.inks.INK_PASS_SKSL
import com.alexgabor.design.riso.risograph.inks.WARP_DP
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PaperSurfaceTest {

    @Test
    fun everyShaderCompiles() {
        listOf(FINE_TILE_BAKE_SKSL, COARSE_TILE_BAKE_SKSL, STOCK_SKSL, INK_PASS_SKSL).forEach {
            RuntimeEffect.makeForShader(it)
        }
    }

    @Test
    fun fineTileRepeatsWithItsPeriod() {
        val period = 128
        val pixels = render(FINE_TILE_BAKE_SKSL, 2 * period) {
            uniform("u_tilePixels", period.toFloat())
            uniform("u_roughCells", 38f)
            uniform("u_fiberCells", 110f)
        }
        assertRepeats(pixels, 2 * period, period)
    }

    @Test
    fun coarseTileRepeatsWithItsPeriod() {
        val period = 96
        val pixels = render(COARSE_TILE_BAKE_SKSL, 2 * period) {
            uniform("u_tilePixels", period.toFloat())
            uniform("u_fadeCells", 15f)
            uniform("u_seedOffset", 58f)
        }
        assertRepeats(pixels, 2 * period, period)
    }

    /**
     * What the sheet decodes out of a baked tile is the field the bake computed, to within the
     * quantization and the dither: a level either way, in the tile's own encoding.
     */
    @Test
    fun decodedTileMatchesTheFieldsItWasBakedFrom() {
        val key = FineTileKey(roughCells = 38, fiberCells = 110, bucket = 1)
        val tile = bake(key)

        val errors = render(FINE_TILE_FIELDS_SKSL + "\n" + DECODE_ERROR_SKSL, key.pixels) {
            uniform("u_tilePixels", key.pixels.toFloat())
            uniform("u_roughCells", key.roughCells.toFloat())
            uniform("u_fiberCells", key.fiberCells.toFloat())
            child("u_tile", tile.makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, SamplingMode.DEFAULT))
        }
        // Errors are written as levels / 16.
        val worstRough = errors.maxOf { red(it) } * 16f / 255f
        val worstFiber = errors.maxOf { green(it) } * 16f / 255f
        assertTrue(worstRough <= 1.1f, "roughness decodes $worstRough levels off")
        assertTrue(worstFiber <= 1.1f, "fiber decodes $worstFiber levels off")
    }

    /** The encoding has room for every value the default stock's fields reach, at full resolution. */
    @Test
    fun defaultFieldsFitTheirEncoding() {
        val key = RisoPaper().fineTileKey(density = 3f)
        val ranges = render(FINE_TILE_FIELDS_SKSL + "\n" + RANGE_SKSL, key.pixels) {
            uniform("u_tilePixels", key.pixels.toFloat())
            uniform("u_roughCells", key.roughCells.toFloat())
            uniform("u_fiberCells", key.fiberCells.toFloat())
        }
        // rough * 0.1 + 0.5, fiber / (2 * FIBER_MAX): both well inside 0..1 if they fit.
        val roughMin = (ranges.minOf { red(it) } / 255f - 0.5f) * 10f
        val roughMax = (ranges.maxOf { red(it) } / 255f - 0.5f) * 10f
        val fiberMax = ranges.maxOf { green(it) } / 255f * 2f * FIBER_MAX
        println("default fields: rough $roughMin..$roughMax, fiber ..$fiberMax")
        assertTrue(roughMin > -0.5f / ROUGH_ENCODE && roughMax < 0.5f / ROUGH_ENCODE, "rough $roughMin..$roughMax")
        assertTrue(fiberMax < FIBER_MAX, "fiber reaches $fiberMax")
    }

    /**
     * The default stock moves the ink by less than a dp anywhere on the sheet: the surface's
     * displacement, `0.75·roughness·rough + 0.2·fiber'` with the fields at their extremes, times
     * [WARP_DP].
     */
    @Test
    fun defaultWarpStaysUnderOneDp() {
        val stock = RisoPaper()
        val key = stock.fineTileKey(density = 3f)
        val ranges = render(FINE_TILE_FIELDS_SKSL + "\n" + RANGE_SKSL, key.pixels) {
            uniform("u_tilePixels", key.pixels.toFloat())
            uniform("u_roughCells", key.roughCells.toFloat())
            uniform("u_fiberCells", key.fiberCells.toFloat())
        }
        val rough = ranges.maxOf { abs(red(it) / 255f - 0.5f) } * 10f
        val fiberGradient = ranges.maxOf { green(it) } / 255f * 2f * FIBER_MAX
        val fiber = 0.5f * stock.fiber * maxOf(fiberGradient - 1f, 1f)
        val worst = WARP_DP * (0.75f * stock.roughness * rough + 0.2f * fiber)
        println("default warp: at most ${worst}dp")
        assertTrue(worst <= 1f, "the default stock moves the ink up to ${worst}dp")
    }

    @Test
    fun aFlatStockNeverWarps() {
        val flat = RisoPaper(roughness = 0f, fiber = 0f)
        val tile = PlaceholderTile
        assertFalse(SheetSurface(flat, tile, tile).ready)
        assertTrue(SheetSurface(RisoPaper(), tile, tile).ready)
        assertFalse(SheetSurface(RisoPaper(), fine = null, coarse = tile).ready)
    }

    @Test
    fun densityBuckets() {
        assertEquals(1, densityBucket(0.75f))
        assertEquals(1, densityBucket(1f))
        assertEquals(2, densityBucket(2f))
        assertEquals(3, densityBucket(2.625f))
        assertEquals(3, densityBucket(3f))
        assertEquals(4, densityBucket(3.5f))
    }

    @Test
    fun tileKeysIgnoreColorsAndStrengths() {
        val stock = RisoPaper()
        val recolored = stock.copy(
            colorFront = Color(0xFFF6D9E1),
            colorBack = Color(0xFFE0D0D0),
            contrast = 0.8f,
            roughness = 0.4f,
            fiber = 0.3f,
            fade = 0.1f,
        )
        assertEquals(stock.fineTileKey(3f), recolored.fineTileKey(3f))
        assertEquals(stock.coarseTileKey(), recolored.coarseTileKey())

        assertNotEquals(stock.fineTileKey(3f), stock.copy(fiberSize = 0.6f).fineTileKey(3f))
        assertNotEquals(stock.coarseTileKey(), stock.copy(seed = 1f).coarseTileKey())
        assertEquals(stock.fineTileKey(2.625f), stock.fineTileKey(3f))
    }

    @Test
    fun separationColorOfTheDefaultStockIsTheStock() {
        val stock = RisoPaper()
        val separated = stock.separationColor!!
        assertEquals(stock.colorFront.red, separated.red, 1e-4f)
        assertEquals(stock.colorFront.green, separated.green, 1e-4f)
        assertEquals(stock.colorFront.blue, separated.blue, 1e-4f)
        assertEquals(null, RisoPaper.None.separationColor)
    }

    private fun bake(key: FineTileKey): Image {
        val pixels = render(FINE_TILE_BAKE_SKSL, key.pixels) {
            uniform("u_tilePixels", key.pixels.toFloat())
            uniform("u_roughCells", key.roughCells.toFloat())
            uniform("u_fiberCells", key.fiberCells.toFloat())
        }
        val bytes = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { i, p ->
            bytes[i * 4] = red(p).toByte()
            bytes[i * 4 + 1] = green(p).toByte()
            bytes[i * 4 + 2] = 0
            bytes[i * 4 + 3] = 0xFF.toByte()
        }
        return Image.makeRaster(
            ImageInfo(key.pixels, key.pixels, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            bytes,
            key.pixels * 4,
        )
    }

    private fun assertRepeats(pixels: IntArray, size: Int, period: Int) {
        var worst = 0
        for (y in 0 until period) for (x in 0 until period) {
            val at = pixels[y * size + x]
            listOf(pixels[y * size + x + period], pixels[(y + period) * size + x]).forEach { other ->
                worst = maxOf(worst, abs(red(at) - red(other)), abs(green(at) - green(other)))
            }
        }
        // The dither is not periodic, so a level either way on each side.
        assertTrue(worst <= 2, "a period apart, pixels differ by up to $worst levels")
    }

    /** Renders [sksl] over a [size]-square raster and returns its pixels as 0xRRGGBBAA. */
    private fun render(sksl: String, size: Int, setup: RuntimeShaderBuilder.() -> Unit): IntArray {
        val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(sksl)).apply(setup)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.drawRect(
            Rect.makeWH(size.toFloat(), size.toFloat()),
            Paint().apply { shader = builder.makeShader() },
        )
        val bitmap = Bitmap().apply {
            allocPixels(ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        }
        surface.readPixels(bitmap, 0, 0)
        val bytes = bitmap.readPixels()!!
        return IntArray(size * size) { i ->
            (bytes[i * 4].toInt() and 0xFF shl 24) or
                (bytes[i * 4 + 1].toInt() and 0xFF shl 16) or
                (bytes[i * 4 + 2].toInt() and 0xFF shl 8) or
                (bytes[i * 4 + 3].toInt() and 0xFF)
        }
    }

    private fun red(pixel: Int) = pixel ushr 24 and 0xFF
    private fun green(pixel: Int) = pixel ushr 16 and 0xFF

    private companion object {
        // language=AGSL
        val DECODE_ERROR_SKSL = """
            uniform shader u_tile;
            uniform float u_tilePixels;
            uniform float u_roughCells;
            uniform float u_fiberCells;

            half4 main(float2 fragCoord) {
                float2 fields = fineFields(fragCoord / u_tilePixels, u_roughCells, u_fiberCells);
                half4 tile = u_tile.eval(fragCoord);
                float roughLevels = abs(float(tile.r) - (fields.x * $ROUGH_ENCODE + 0.5)) * 255.0;
                float fiberLevels = abs(float(tile.g) - clamp(fields.y / $FIBER_MAX, 0.0, 1.0)) * 255.0;
                return half4(half(roughLevels / 16.0), half(fiberLevels / 16.0), 0.0, 1.0);
            }
        """.trimIndent()

        // language=AGSL
        val RANGE_SKSL = """
            uniform float u_tilePixels;
            uniform float u_roughCells;
            uniform float u_fiberCells;

            half4 main(float2 fragCoord) {
                float2 fields = fineFields(fragCoord / u_tilePixels, u_roughCells, u_fiberCells);
                return half4(half(fields.x * 0.1 + 0.5), half(fields.y / (2.0 * $FIBER_MAX)), 0.0, 1.0);
            }
        """.trimIndent()
    }
}
