package com.alexgabor.design.riso.risograph

import org.jetbrains.skia.RuntimeShaderBuilder

/** [ShaderUniforms] over a skia runtime shader builder. */
internal class RuntimeShaderBuilderUniforms(
    private val builder: RuntimeShaderBuilder,
) : ShaderUniforms {
    override fun float(name: String, value: Float) = builder.uniform(name, value)

    override fun float2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

    override fun float3(name: String, x: Float, y: Float, z: Float) =
        builder.uniform(name, x, y, z)

    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
        builder.uniform(name, x, y, z, w)

    override fun floats(name: String, values: FloatArray) = builder.uniform(name, values)
}
