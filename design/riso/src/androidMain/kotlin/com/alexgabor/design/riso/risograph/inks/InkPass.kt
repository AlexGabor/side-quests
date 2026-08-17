package com.alexgabor.design.riso.risograph.inks

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.alexgabor.design.riso.risograph.RuntimeShaderUniforms

/**
 * One drum's pass, as an AGSL effect over the recorded artwork.
 *
 * The shader is compiled once and re-uniformed per draw — a `RenderEffect` bakes in its shader's
 * uniforms, so the effect itself is rebuilt each time, but the compile is not.
 */
internal actual class InkPass actual constructor() {

    private val shader = RuntimeShader(INK_PASS_SKSL)
    private val uniforms = RuntimeShaderUniforms(shader)

    actual fun effect(spec: InkPassSpec): RenderEffect? {
        uniforms.setInkPass(spec)
        return AndroidRenderEffect
            .createRuntimeShaderEffect(shader, "u_image")
            .asComposeRenderEffect()
    }
}
