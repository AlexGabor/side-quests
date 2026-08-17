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
 * The effect is compiled once and re-uniformed per draw — an `ImageFilter` bakes in its builder's
 * uniforms, so the filter itself is rebuilt each time, but the compile is not. The builder is held
 * alongside it for the same reason.
 */
internal actual class InkPass actual constructor() {

    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(INK_PASS_SKSL))
    private val uniforms = RuntimeShaderBuilderUniforms(builder)

    actual fun effect(spec: InkPassSpec): RenderEffect? {
        uniforms.setInkPass(spec)
        // A null input filter is the source itself, so `u_image` reads the layer this hangs on —
        // the same thing Android's createRuntimeShaderEffect names.
        return ImageFilter.makeRuntimeShader(builder, "u_image", null).asComposeRenderEffect()
    }
}
