package com.alexgabor.design.riso.risograph.paper

import com.alexgabor.design.riso.risograph.RuntimeShaderBuilderUniforms
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
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import kotlin.collections.getOrPut

/**
 * The paper surface, baked. Everything in this file runs once per stock and layout rather than per
 * frame: the folds, fiber and roughness of a sheet do not depend on what is printed on it, so the
 * expensive procedural pass is rendered into a texture that [risoPaper]'s shader then samples.
 */

/**
 * The baked surface for [paper] at this size, as a shader ready to bind to `u_paperMap`.
 *
 * A stock with neither roughness nor fiber has the same value at every pixel, so it is baked 1x1 and
 * stretched: every size and every call site then shares the one entry, and [RisoPaper.None] costs no
 * surface at all.
 */
internal fun paperMapShader(
    paper: RisoPaper,
    width: Int,
    height: Int,
    density: Float,
): Shader {
    val flat = !paper.warps
    val w = if (flat) 1 else width
    val h = if (flat) 1 else height
    val image = PaperMapCache.get(PaperMapKey(paper, w, h, density)) {
        bakePaperMap(paper, w, h, density)
    }
    return image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
}

/**
 * Renders [PAPER_BAKE_SKSL] once into an [Image], encoding the content-UV distortion vector and the
 * lighting term (see the shader). Skia runs a runtime effect on a raster surface directly, so unlike
 * Android — where `RuntimeShader` executes only under hardware rendering — this is a plain draw.
 */
private fun bakePaperMap(paper: RisoPaper, width: Int, height: Int, density: Float): Image {
    val builder = RuntimeShaderBuilder(BakeEffect)
    RuntimeShaderBuilderUniforms(builder)
        .setPaperBake(paper, width.toFloat(), height.toFloat(), density)
    builder.child("u_noiseTexture", createNoiseShader())

    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawRect(
        Rect.makeWH(width.toFloat(), height.toFloat()),
        Paint().apply { shader = builder.makeShader() },
    )
    val image = surface.makeImageSnapshot()
    surface.close()
    return image
}

/**
 * The bake's compiled shader, held across bakes. Compiling the SkSL is the fixed cost of a bake and
 * has nothing to do with the size being baked — which matters here, where a stock is baked once at
 * 1x1 to stand in and then again at every size the layout settles at.
 */
private val BakeEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(PAPER_BAKE_SKSL) }

private data class PaperMapKey(
    val paper: RisoPaper,
    val width: Int,
    val height: Int,
    val density: Float,
)

/**
 * Process-wide cache of baked paper maps so that many composables sharing the same stock and size
 * (e.g. list items) reuse a single texture instead of each re-running the heavy bake.
 */
private object PaperMapCache {
    private const val MAX_ENTRIES = 8
    private val entries = object : LinkedHashMap<PaperMapKey, Image>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaperMapKey, Image>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: PaperMapKey, produce: () -> Image): Image =
        entries.getOrPut(key, produce)
}

/** [noisePixels] as a repeat-tiled, linearly filtered shader. */
private fun createNoiseShader(size: Int = NOISE_SIZE): Shader {
    // RGBA_8888 rather than N32, so the byte order the channels are written in is the one skia
    // reads back regardless of the host's endianness — the surface samples r, g and b as three
    // independent random fields, and a swizzle would silently shuffle which is which.
    val pixels = noisePixels(size)
    val bytes = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, argb ->
        bytes[index * 4] = (argb shr 16).toByte()    // r
        bytes[index * 4 + 1] = (argb shr 8).toByte() // g
        bytes[index * 4 + 2] = argb.toByte()         // b
        bytes[index * 4 + 3] = (argb shr 24).toByte() // a, always opaque
    }
    val info = ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, size * 4)
        .makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, SamplingMode.LINEAR)
}
