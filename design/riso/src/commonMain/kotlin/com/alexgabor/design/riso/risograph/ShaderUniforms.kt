package com.alexgabor.design.riso.risograph

import androidx.compose.ui.graphics.Color

/**
 * Somewhere a shader's uniforms can be written.
 *
 * The shaders are shared between Android and the JVM but the objects that carry them are not:
 * Android sets uniforms on a `RuntimeShader`, skiko on a `RuntimeShaderBuilder`. Writing to this
 * instead lets the uniform *names*, their order and the coercions applied on the way in live beside
 * the shader source they belong to, so a change to one is a change to both platforms rather than an
 * invitation for them to drift.
 */
internal interface ShaderUniforms {
    fun float(name: String, value: Float)
    fun float2(name: String, x: Float, y: Float)
    fun float3(name: String, x: Float, y: Float, z: Float)
    fun float4(name: String, x: Float, y: Float, z: Float, w: Float)

    /** An array uniform. Must be exactly the length the shader was compiled with. */
    fun floats(name: String, values: FloatArray)
}

internal fun ShaderUniforms.float3(name: String, values: FloatArray) =
    float3(name, values[0], values[1], values[2])

internal fun ShaderUniforms.color(name: String, color: Color) =
    float4(name, color.red, color.green, color.blue, color.alpha)
