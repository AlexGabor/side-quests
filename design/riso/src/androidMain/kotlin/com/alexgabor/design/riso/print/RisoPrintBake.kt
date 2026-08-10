package com.alexgabor.design.riso.print

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.alexgabor.design.riso.bypass.applyBypass
import com.alexgabor.design.riso.bypass.bypassCapacity

/**
 * A single sheet off the press, as a [Bitmap]: [content] is drawn, printed by the same shader
 * [risoPrint] uses, and read back.
 *
 * The modifier can only print what a composition is already drawing on screen, which is no use for
 * artwork that has to end up as a static asset — a launcher icon, a share image, anything the
 * platform wants as a drawable rather than as a frame. This runs the identical pipeline once,
 * without a composition: the artwork is recorded into a `RenderNode`, the print shader is installed
 * on that node as its render effect, and the node is rendered through a [HardwareRenderer]. Same
 * separation, same registration error, same grain.
 *
 * [density] is what every length in [params] is measured against — the grain, the mottle and the
 * registration offsets are authored in dp, so it decides their *physical* size in the result rather
 * than the result's resolution. Bake at the density the artwork will be seen at, not at the one that
 * happens to give a round pixel count.
 *
 * `RuntimeShader` only executes under hardware rendering, so this needs a real GPU: it runs on a
 * device or emulator, not under Layoutlib or on the JVM target.
 */
fun bakeRisoPrint(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    params: RisoPrintParams = RisoPrintParams(),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    content: DrawScope.() -> Unit,
): Bitmap {
    require(widthPx > 0 && heightPx > 0) { "bakeRisoPrint needs a non-empty size" }

    val inkCapacity = inkCapacity(params.inks.size)
    val bypassCapacity = bypassCapacity(0)
    val shader = RuntimeShader(risoPrintAgsl(bypassCapacity, inkCapacity))
    // Nothing is bypassed offscreen — there are no children to report bounds — but the uniforms
    // still have to be written before the shader runs.
    shader.applyBypass(emptyList(), bypassCapacity)
    shader.applyRisoParams(
        params = params,
        capacity = inkCapacity,
        width = widthPx.toFloat(),
        height = heightPx.toFloat(),
        density = density,
        paperMap = paperMapShader(params.paper, widthPx, heightPx, density),
    )

    // The artwork and the press, in that order: the effect is installed on the node that records the
    // drawing, so the node's own content is what arrives as `u_image`. This is what `graphicsLayer`
    // does, one layer down.
    val artwork = RenderNode("risoPrintBake").apply {
        setPosition(0, 0, widthPx, heightPx)
        setClipToBounds(true)
        setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "u_image"))
    }
    val bounds = Size(widthPx.toFloat(), heightPx.toFloat())
    CanvasDrawScope().draw(
        density = Density(density),
        layoutDirection = layoutDirection,
        canvas = Canvas(artwork.beginRecording()),
        size = bounds,
    ) {
        // A node that records nothing is never rasterized, and an effect on it never runs — a blank
        // sheet would come back empty rather than as paper. Clearing the full bounds first is a real
        // draw, so the press runs across the whole node however little is printed on it.
        drawRect(color = Color.Transparent, size = bounds, blendMode = BlendMode.Src)
        content()
    }
    artwork.endRecording()

    // A render effect applies as the node is drawn *into* something, so the effect-bearing node is
    // played back into a plain root rather than being the content root itself.
    val root = RenderNode("risoPrintBakeRoot").apply { setPosition(0, 0, widthPx, heightPx) }
    root.beginRecording().drawRenderNode(artwork)
    root.endRecording()

    return renderToBitmap(root, widthPx, heightPx)
}

/**
 * Plays [node] into an offscreen surface and copies the result back into software.
 *
 * The renderer is left non-opaque: a stock with no sheet prints ink on nothing, and an opaque
 * surface would clear that to black instead of leaving it transparent.
 */
private fun renderToBitmap(node: RenderNode, width: Int, height: Int): Bitmap {
    val imageReader = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    val renderer = HardwareRenderer()
    try {
        renderer.setSurface(imageReader.surface)
        renderer.setOpaque(false)
        renderer.setContentRoot(node)
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

        val image = checkNotNull(imageReader.acquireNextImage()) { "the press handed back no sheet" }
        try {
            val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, null))
            return hardware.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            image.close()
        }
    } finally {
        renderer.destroy()
        imageReader.close()
    }
}
