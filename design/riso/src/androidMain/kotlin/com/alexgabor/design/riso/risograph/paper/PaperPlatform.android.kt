package com.alexgabor.design.riso.risograph.paper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.alexgabor.design.riso.risograph.RuntimeShaderUniforms
import com.alexgabor.design.riso.risograph.ShaderUniforms
import java.io.ByteArrayOutputStream
import java.io.File
import android.graphics.Shader as AndroidShader

/**
 * Renders one tile on the GPU. `RuntimeShader` only executes under hardware rendering, so this draws
 * through a [HardwareRenderer] into an [ImageReader] surface and reads the result back to the CPU —
 * which is also what makes the tile safe to sample from Compose's own renderer.
 */
internal actual suspend fun bakeTile(
    sksl: String,
    pixels: Int,
    uniforms: (ShaderUniforms) -> Unit,
): ImageBitmap {
    val shader = RuntimeShader(sksl)
    uniforms(RuntimeShaderUniforms(shader))

    val imageReader = ImageReader.newInstance(
        pixels,
        pixels,
        PixelFormat.RGBA_8888,
        // Two, not one: the renderer dequeues a buffer to draw into while the reader can hold
        // another, and with only one to go round `syncAndDraw` waits for a present that never comes.
        2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    val renderer = HardwareRenderer()
    try {
        renderer.setSurface(imageReader.surface)
        val node = RenderNode("risoPaperTile")
        node.setPosition(0, 0, pixels, pixels)
        val canvas = node.beginRecording()
        canvas.drawRect(0f, 0f, pixels.toFloat(), pixels.toFloat(), Paint().apply { this.shader = shader })
        node.endRecording()
        renderer.setContentRoot(node)
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

        val image = requireNotNull(imageReader.acquireNextImage()) { "The paper tile bake produced no image" }
        try {
            val hardware = requireNotNull(Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, null))
            return hardware.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
        } finally {
            image.close()
        }
    } finally {
        renderer.destroy()
        imageReader.close()
    }
}

internal actual fun ImageBitmap.tileShader(): Shader =
    BitmapShader(asAndroidBitmap(), AndroidShader.TileMode.REPEAT, AndroidShader.TileMode.REPEAT)
        .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }

internal actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().use { out ->
        asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

internal actual fun readTileFile(dir: String, name: String): ByteArray? =
    File(dir, name).takeIf { it.isFile }?.runCatching { readBytes() }?.getOrNull()

internal actual fun writeTileFile(dir: String, name: String, bytes: ByteArray) {
    runCatching {
        val folder = File(dir).apply { mkdirs() }
        // Written aside and moved into place, so a reader never sees half a tile.
        val partial = File(folder, "$name.partial")
        partial.writeBytes(bytes)
        partial.renameTo(File(folder, name))
    }
}

/** The app's cache folder, which the system may clear — a tile lost there is only baked again. */
@Composable
@ReadOnlyComposable
internal actual fun tileCacheDir(): String? = "${LocalContext.current.cacheDir.absolutePath}/riso-tiles"

internal actual class StockBrush actual constructor() {

    private val shader = RuntimeShader(STOCK_SKSL)
    private val uniforms = RuntimeShaderUniforms(shader)

    private var held: Pair<SheetSurface, Float>? = null

    actual fun shader(surface: SheetSurface, density: Float): Shader {
        val key = surface to density
        if (held != key) {
            uniforms.setSheetSurface(surface.paper, density, surface.ready)
            uniforms.setStock(surface.paper)
            shader.setInputShader("u_fineTile", surface.fine ?: PlaceholderTile)
            shader.setInputShader("u_coarseTile", surface.coarse ?: PlaceholderTile)
            held = key
        }
        return shader
    }
}
