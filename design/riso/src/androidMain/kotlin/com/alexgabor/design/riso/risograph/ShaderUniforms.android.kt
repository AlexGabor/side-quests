package com.alexgabor.design.riso.risograph

import android.graphics.RuntimeShader

/** [ShaderUniforms] over an Android runtime shader. */
internal class RuntimeShaderUniforms(private val shader: RuntimeShader) : ShaderUniforms {
    override fun float(name: String, value: Float) = shader.setFloatUniform(name, value)

    override fun float2(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)

    override fun float3(name: String, x: Float, y: Float, z: Float) =
        shader.setFloatUniform(name, x, y, z)

    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
        shader.setFloatUniform(name, x, y, z, w)

    override fun floats(name: String, values: FloatArray) = shader.setFloatUniform(name, values)
}
