package com.alexgabor.design.riso.risograph.region

import android.graphics.RuntimeShader

/**
 * The AGSL every effect shader shares in order to honour [risoBypass]: the bypassed rectangles, and
 * a mask that is 1 inside them.
 *
 * A shader's uniform array lengths are fixed when it is compiled, so [capacity] is baked into the
 * source. Capacities are bucketed by [bypassCapacity] so that adding or removing a region rarely
 * means compiling anything.
 */
// language=AGSL
internal fun bypassAgsl(capacity: Int): String = """
uniform float4 u_bypass[$capacity];      // Region bounds as xywh, in layer pixels.
uniform float u_bypassRadius[$capacity]; // Corner radius of each region, in pixels.
uniform float u_bypassCount;

/**
 * How much of a pixel is bypassed: 1 well inside a region, 0 outside, with a pixel of coverage
 * across the edge so the seam between printed and untouched is not a staircase.
 */
float bypassMask(float2 p) {
    float mask = 0.0;
    for (int i = 0; i < $capacity; i++) {
        if (float(i) >= u_bypassCount) break;

        float4 region = u_bypass[i];
        float radius = u_bypassRadius[i];
        float2 halfSize = 0.5 * region.zw;
        // Rounded-box signed distance: negative inside the region, positive outside.
        float2 q = abs(p - (region.xy + halfSize)) - (halfSize - radius);
        float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;

        mask = max(mask, 1.0 - smoothstep(-0.5, 0.5, sd));
    }
    return mask;
}
""".trimIndent()

/** Uniform-array capacity for [count] bypassed regions. See [regionCapacity]. */
internal fun bypassCapacity(count: Int): Int = regionCapacity(count)

/**
 * Sets the bypass uniforms, clamped to the shader's own [capacity]. The clamp only bites in the one
 * frame between a region appearing and the recomposition that widens the arrays landing, and only
 * when the count crosses a bucket; the overflow is printed rather than bypassed until then.
 */
internal fun RuntimeShader.applyBypass(regions: List<RisoBypassRect>, capacity: Int) {
    val count = minOf(regions.size, capacity)
    setFloatUniform("u_bypassCount", count.toFloat())

    // Slots past the count stay zeroed; the shader's loop stops before it reaches them.
    val bounds = FloatArray(capacity * 4)
    val radii = FloatArray(capacity)
    repeat(count) { index ->
        val region = regions[index]
        bounds[index * 4] = region.rect.left
        bounds[index * 4 + 1] = region.rect.top
        bounds[index * 4 + 2] = region.rect.width
        bounds[index * 4 + 3] = region.rect.height
        radii[index] = region.cornerRadiusPx
    }
    setFloatUniform("u_bypass", bounds)
    setFloatUniform("u_bypassRadius", radii)
}
