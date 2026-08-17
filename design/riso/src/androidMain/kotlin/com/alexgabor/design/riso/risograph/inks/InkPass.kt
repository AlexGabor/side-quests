package com.alexgabor.design.riso.risograph.inks

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.alexgabor.design.riso.risograph.RuntimeShaderUniforms

/**
 * One drum's pass, as an AGSL effect over the recorded artwork.
 *
 * The shader is compiled once and re-uniformed only when the spec changes — a `RenderEffect` bakes in
 * its shader's uniforms, so the effect has to be rebuilt whenever they move, but neither the compile
 * nor the rebuild belongs on a frame where nothing did.
 */
internal actual class InkPass actual constructor() {

    private val shader = RuntimeShader(INK_PASS_SKSL)
    private val uniforms = RuntimeShaderUniforms(shader)

    private var held: InkPassSpec? = null
    private var effect: RenderEffect? = null

    actual fun effect(spec: InkPassSpec): RenderEffect? {
        effect?.let { if (held == spec) return it }
        uniforms.setInkPass(spec)
        return AndroidRenderEffect
            .createRuntimeShaderEffect(shader, "u_image")
            .asComposeRenderEffect()
            .also {
                held = spec
                effect = it
            }
    }
}
