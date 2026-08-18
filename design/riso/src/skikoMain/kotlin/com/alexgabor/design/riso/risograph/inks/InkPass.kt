package com.alexgabor.design.riso.risograph.inks

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.alexgabor.design.riso.risograph.RuntimeShaderBuilderUniforms
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * One drum's pass, as a skia runtime shader over the recorded artwork.
 *
 * The effect is compiled once and re-uniformed only when the spec changes — an `ImageFilter` bakes in
 * its builder's uniforms, so the filter has to be rebuilt whenever they move, but neither the compile
 * nor the rebuild belongs on a frame where nothing did. The builder is held alongside it for the same
 * reason.
 */
internal actual class InkPass actual constructor() {

    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(INK_PASS_SKSL))
    private val uniforms = RuntimeShaderBuilderUniforms(builder)

    private var held: InkPassSpec? = null
    private var effect: RenderEffect? = null

    actual fun effect(spec: InkPassSpec): RenderEffect? {
        effect?.let { if (held == spec) return it }
        uniforms.setInkPass(spec)
        // A null input filter is the source itself, so `u_image` reads the layer this hangs on —
        // the same thing Android's createRuntimeShaderEffect names.
        return ImageFilter.makeRuntimeShader(builder, "u_image", null)
            .asComposeRenderEffect()
            .also {
                held = spec
                effect = it
            }
    }
}
