package com.alexgabor.design.riso.separation

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.print.densityOf
import com.alexgabor.design.riso.print.dot3

/**
 * The AGSL the print shader needs in order to honour [risoInk]: the regions that name their own
 * drums, and the rack to print a pixel in one of them with.
 *
 * A shader's uniform array lengths are fixed when it is compiled, so [capacity] is baked into the
 * source, bucketed the same way the bypassed regions are.
 */
// language=AGSL
internal fun inkIntentAgsl(capacity: Int): String = """
uniform float4 u_intent[$capacity];      // Region bounds as xywh, in layer pixels.
uniform float4 u_intentSlots[$capacity]; // xyz: the drums it prints with; w: corner radius, pixels.
uniform float4 u_intentRow0[$capacity];  // xyz: coverage row for slot x; w: the offset amplifier.
uniform float4 u_intentRow1[$capacity];  // xyz: coverage row for slot y.
uniform float4 u_intentRow2[$capacity];  // xyz: coverage row for slot z.
uniform float u_intentCount;

/**
 * The rack a pixel was told to print with, and how far off-register to run it, if any region claims
 * it. False leaves the arguments as they were, for the separation to decide as it always has —
 * which is why they are `inout` and not `out`: an `out` parameter is write-only, and copying one
 * back unwritten would wipe the rows the fan had just worked out for every unclaimed pixel.
 *
 * [reach] is how far outside a region still counts as claimed — zero for a pixel with artwork of its
 * own, and a pass's drift for one on bare paper, since the only ink that can reach bare paper is a
 * neighbouring pass wandering off its region.
 *
 * The rows are the same shape [selectWedge] produces, so everything downstream is none the wiser:
 * coverage is still `dot(row, density)`. What changes is that they are built from the recipe the
 * author named rather than from a fit against the whole rack — see applyInk().
 */
bool selectIntent(
    float2 p,
    float reach,
    inout float3 slots,
    inout float3 row0,
    inout float3 row1,
    inout float3 row2,
    inout float offsetScale
) {
    bool claimed = false;
    float tightest = 0.0;
    for (int i = 0; i < $capacity; i++) {
        if (float(i) >= u_intentCount) break;

        float4 region = u_intent[i];
        float radius = u_intentSlots[i].w;
        float2 halfSize = 0.5 * region.zw;
        // Rounded-box signed distance: negative inside the region, positive outside.
        float2 q = abs(p - (region.xy + halfSize)) - (halfSize - radius);
        float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        if (sd > reach) continue;

        // The smallest claim wins, which is the innermost: a composable is laid out inside its
        // ancestors, so a nested region is always contained in the one it sits in. Order would be
        // the obvious tiebreak and is the wrong one — regions arrive in the order their modifier
        // nodes attached, which puts a child before its parent as often as not.
        float area = region.z * region.w;
        if (claimed && area >= tightest) continue;

        tightest = area;
        slots = u_intentSlots[i].xyz;
        row0 = u_intentRow0[i].xyz;
        row1 = u_intentRow1[i].xyz;
        row2 = u_intentRow2[i].xyz;
        offsetScale = u_intentRow0[i].w;
        claimed = true;
    }
    return claimed;
}

// No edge feathering, unlike the bypass mask: how much ink lands is still read per pixel from the
// artwork, so the edge of anything drawn inside a region is antialiased by its own coverage. The
// region's own boundary normally sits out where there is nothing printed for it to cut.
""".trimIndent()

/**
 * Sets the ink intent uniforms, clamped to the shader's own [capacity], resolving each region's
 * recipe against the [rack] the press is actually running.
 *
 * ### How a recipe becomes coverage rows
 * A recipe of coverages `c` on inks `i` has its own optical density `D = sum(c_i * density(ink_i))`,
 * and a pixel's coverage of drum `i` is the pixel's density projected onto that one axis:
 *
 *     row_i = c_i * D / dot(D, D)     so that     dot(row_i, pixel) = c_i * s
 *
 * where `s` is the least-squares fit of the pixel against the recipe. At the authored colour `s` is
 * exactly 1 and every drum gets the coverage it was given, which is what makes a fill authored with
 * [risoOverprint][com.alexgabor.design.riso.print.risoOverprint] round-trip; at a tint, a gradient
 * or an antialiased edge `s` falls off with the ink, so tone still comes from the artwork.
 *
 * A recipe that names nothing has no axis to project onto, and leaves its slots and rows zeroed: no
 * drum runs, and the region prints as bare stock.
 */
internal fun RuntimeShader.applyInk(
    regions: List<RisoInkRect>,
    capacity: Int,
    rack: List<Color>,
    driftPx: Float,
    wobblePx: Float,
) {
    val count = minOf(regions.size, capacity)
    setFloatUniform("u_intentCount", count.toFloat())

    // Slots past the count stay zeroed; the shader's loop stops before it reaches them.
    val bounds = FloatArray(capacity * 4)
    val slots = FloatArray(capacity * 4)
    val rows = Array(3) { FloatArray(capacity * 4) }

    var widestScale = 1f
    repeat(count) { index ->
        val region = regions[index]
        bounds[index * 4] = region.rect.left
        bounds[index * 4 + 1] = region.rect.top
        bounds[index * 4 + 2] = region.rect.width
        bounds[index * 4 + 3] = region.rect.height
        slots[index * 4 + 3] = region.cornerRadiusPx
        rows[0][index * 4 + 3] = region.offsetScale
        widestScale = maxOf(widestScale, region.offsetScale)

        // A colour prints on at most three drums, as a wedge of the fan does, so a longer recipe
        // leaves the drums it leans on least in the rack.
        val recipe = region.recipe
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(3)

        val axis = FloatArray(3)
        val drums = IntArray(recipe.size)
        recipe.forEachIndexed { corner, (color, coverage) ->
            drums[corner] = slotOf(color, rack)
            val density = densityOf(color)
            repeat(3) { channel -> axis[channel] += coverage * density[channel] }
        }

        // Nothing to print, or a recipe so pale it has no direction to fit against. Either way the
        // zeroed slots and rows leave the region blank — and the zeroed slots matter, because the
        // shader gathers a pass off them before it knows the coverage is zero.
        val norm = dot3(axis, axis)
        if (norm <= 1e-6f) return@repeat

        recipe.forEachIndexed { corner, (_, coverage) ->
            slots[index * 4 + corner] = drums[corner].toFloat()
            repeat(3) { channel ->
                rows[corner][index * 4 + channel] = coverage * axis[channel] / norm
            }
        }
    }

    setFloatUniform("u_intent", bounds)
    setFloatUniform("u_intentSlots", slots)
    setFloatUniform("u_intentRow0", rows[0])
    setFloatUniform("u_intentRow1", rows[1])
    setFloatUniform("u_intentRow2", rows[2])

    // How far a pass can land from where it was drawn, which is how far a blank pixel has to look to
    // find the artwork whose ink might drift onto it. An amplified region throws its passes further
    // than the rack alone would, and a probe that did not know that would clip the fringe at exactly
    // the radius the amplifier was reaching past. Sized off the widest amplifier on screen rather
    // than per region, which is free: the probe is four taps whatever the radius. The feed wobble is
    // not amplified, so it is added after.
    setFloatUniform("u_probeRadius", driftPx * widestScale + wobblePx)
}

/**
 * Which drum on the press carries [color].
 *
 * Exact first — an author naming an ink means that ink, and the fluorescents in particular are close
 * enough to their neighbours in density that a fit would not reliably pick them out. Failing that,
 * the nearest drum by density, so a colour that was never loaded still prints on the closest thing
 * the press has rather than falling off it.
 */
private fun slotOf(color: Color, rack: List<Color>): Int {
    val exact = rack.indexOfFirst { it.value == color.value }
    if (exact >= 0) return exact

    val target = densityOf(color)
    return rack.indices.minByOrNull { slot ->
        val density = densityOf(rack[slot])
        var sum = 0f
        repeat(3) { channel ->
            val delta = density[channel] - target[channel]
            sum += delta * delta
        }
        sum
    } ?: 0
}
