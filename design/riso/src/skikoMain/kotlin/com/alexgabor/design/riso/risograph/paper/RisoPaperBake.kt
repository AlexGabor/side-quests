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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
 *
 * Held as an immutable snapshot swapped under compare-and-set rather than a `LinkedHashMap` under a
 * lock, because this is shared with iOS, where neither `removeEldestEntry` nor `@Synchronized`
 * exists. The bake is not run inside the swap: two threads racing on the same key both bake it and
 * one snapshot wins, which costs a duplicate bake and nothing else. That is already the outcome the
 * callers tolerate — a bake is a pure function of its key, so either result is the same texture —
 * and it is worth more than holding a lock across the better part of a second of raster work.
 */
private object PaperMapCache {
    private const val MAX_ENTRIES = 8

    /** Keys in least-recently-used order: [Entries.order] `[0]` is the next one evicted. */
    private class Entries(val map: Map<PaperMapKey, Image>, val order: List<PaperMapKey>)

    @OptIn(ExperimentalAtomicApi::class)
    private val entries = AtomicReference(Entries(emptyMap(), emptyList()))

    @OptIn(ExperimentalAtomicApi::class)
    fun get(key: PaperMapKey, produce: () -> Image): Image {
        entries.load().let { held ->
            held.map[key]?.let { image ->
                touch(held, key)
                return image
            }
        }

        val image = produce()

        while (true) {
            val held = entries.load()
            // Lost the race: whoever else baked this key got there first, and their texture is the
            // one already handed out. Drop ours rather than replace what is in use.
            held.map[key]?.let { return it }

            val order = held.order + key
            val next = if (order.size > MAX_ENTRIES) {
                Entries(held.map - order.first() + (key to image), order.drop(1))
            } else {
                Entries(held.map + (key to image), order)
            }
            if (entries.compareAndSet(held, next)) return image
        }
    }

    /**
     * Moves [key] to the most-recently-used end. Best effort: a lost swap means one entry keeps a
     * staler position in the eviction order, which is a cache that evicts slightly less well rather
     * than one that is wrong.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun touch(held: Entries, key: PaperMapKey) {
        if (held.order.lastOrNull() == key) return
        entries.compareAndSet(held, Entries(held.map, held.order - key + key))
    }
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
