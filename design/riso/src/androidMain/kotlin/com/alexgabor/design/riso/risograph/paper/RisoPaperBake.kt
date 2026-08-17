package com.alexgabor.design.riso.risograph.paper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import com.alexgabor.design.riso.risograph.RuntimeShaderUniforms
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
): BitmapShader {
    val flat = !paper.warps
    val w = if (flat) 1 else width
    val h = if (flat) 1 else height
    val bitmap = PaperMapCache.get(PaperMapKey(paper, w, h, density)) {
        bakePaperMap(paper, w, h, density)
    }
    return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
}

/**
 * Renders [PAPER_BAKE_SKSL] once into a [Bitmap], encoding the content-UV distortion vector and the
 * lighting term (see the shader). `RuntimeShader` only executes under hardware rendering, so this
 * draws through a [HardwareRenderer] into an [ImageReader] surface and reads the result back.
 */
private fun bakePaperMap(paper: RisoPaper, width: Int, height: Int, density: Float): Bitmap {
    val shader = RuntimeShader(PAPER_BAKE_SKSL)
    RuntimeShaderUniforms(shader)
        .setPaperBake(paper, width.toFloat(), height.toFloat(), density)
    shader.setInputShader("u_noiseTexture", createNoiseShader())

    val imageReader = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    val renderer = HardwareRenderer()
    renderer.setSurface(imageReader.surface)
    val node = RenderNode("risoPaperBake")
    node.setPosition(0, 0, width, height)
    val canvas = node.beginRecording()
    canvas.drawRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        Paint().apply { this.shader = shader },
    )
    node.endRecording()
    renderer.setContentRoot(node)
    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

    val image = imageReader.acquireNextImage()
    val hwBitmap = Bitmap.wrapHardwareBuffer(image!!.hardwareBuffer!!, null)!!
    val bitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)

    image.close()
    renderer.destroy()
    imageReader.close()
    return bitmap
}

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
    private val entries = object : LinkedHashMap<PaperMapKey, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaperMapKey, Bitmap>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: PaperMapKey, produce: () -> Bitmap): Bitmap =
        entries.getOrPut(key, produce)
}

/** [noisePixels] as a repeat-tiled, linearly filtered shader. */
private fun createNoiseShader(size: Int = NOISE_SIZE): BitmapShader {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(noisePixels(size), 0, size, 0, 0, size, size)
    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
}
