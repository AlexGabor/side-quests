package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.skiaShader
import com.alexgabor.design.riso.risograph.RuntimeShaderBuilderUniforms
import com.alexgabor.design.riso.risograph.ShaderUniforms
import kotlinx.coroutines.yield
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * Somewhere to draw one bake: a GPU render target where there is one to be had, a raster surface
 * where there is not. Whatever it is, the pixels are read back to the CPU afterwards, so the tile
 * belongs to no GPU context in particular — Compose draws through a different one.
 */
internal expect fun newBakeSurface(width: Int, height: Int): Surface

/** Rows drawn between yields, so that a raster bake on a single thread does not stall frames. */
private const val BAKE_BAND_ROWS = 32

internal actual suspend fun bakeTile(
    sksl: String,
    pixels: Int,
    uniforms: (ShaderUniforms) -> Unit,
): ImageBitmap {
    val builder = RuntimeShaderBuilder(bakeEffect(sksl))
    uniforms(RuntimeShaderBuilderUniforms(builder))
    val paint = Paint().apply { shader = builder.makeShader() }

    val surface = newBakeSurface(pixels, pixels)
    var top = 0
    while (top < pixels) {
        val bottom = minOf(top + BAKE_BAND_ROWS, pixels)
        surface.canvas.drawRect(Rect.makeLTRB(0f, top.toFloat(), pixels.toFloat(), bottom.toFloat()), paint)
        // A no-op on a raster surface and needed on a GPU one, so not worth branching on.
        surface.flushAndSubmit(syncCpu = true)
        top = bottom
        yield()
    }

    val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(pixels, pixels)) }
    surface.readPixels(bitmap, 0, 0)
    bitmap.setImmutable()
    surface.close()
    return bitmap.asComposeImageBitmap()
}

/** The two bake shaders, each compiled once — compiling is the fixed cost of a bake. */
private val FineBakeEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(FINE_TILE_BAKE_SKSL) }
private val CoarseBakeEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(COARSE_TILE_BAKE_SKSL) }

private fun bakeEffect(sksl: String): RuntimeEffect = when (sksl) {
    FINE_TILE_BAKE_SKSL -> FineBakeEffect
    COARSE_TILE_BAKE_SKSL -> CoarseBakeEffect
    else -> RuntimeEffect.makeForShader(sksl)
}

internal actual fun ImageBitmap.tileShader(): Shader =
    Image.makeFromBitmap(asSkiaBitmap())
        .makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, SamplingMode.LINEAR)
        .asComposeShader()

internal actual fun ImageBitmap.encodePng(): ByteArray =
    requireNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)) {
        "Could not encode a paper tile"
    }.bytes

private val StockEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(STOCK_SKSL) }

internal actual class StockBrush actual constructor() {

    private val builder = RuntimeShaderBuilder(StockEffect)
    private val uniforms = RuntimeShaderBuilderUniforms(builder)

    private var held: Pair<SheetSurface, Float>? = null
    private var shader: Shader? = null

    actual fun shader(surface: SheetSurface, density: Float): Shader {
        val key = surface to density
        shader?.let { if (held == key) return it }
        uniforms.setSheetSurface(surface.paper, density, surface.ready)
        uniforms.setStock(surface.paper)
        builder.child("u_fineTile", (surface.fine ?: PlaceholderTile).skiaShader)
        builder.child("u_coarseTile", (surface.coarse ?: PlaceholderTile).skiaShader)
        return builder.makeShader().asComposeShader().also {
            held = key
            shader = it
        }
    }
}
