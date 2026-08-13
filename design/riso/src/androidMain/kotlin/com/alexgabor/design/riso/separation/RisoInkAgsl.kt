package com.alexgabor.design.riso.separation

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.print.densityOf
import com.alexgabor.design.riso.print.separationRows

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
// How far a region's claim carries past its own bounds, in pixels: the furthest any pass on the
// sheet drifts, once amplified. Only bare paper is claimed at a distance — see selectIntent().
uniform float u_intentReach;

/** Rounded-box signed distance: negative inside the region, positive outside. */
float regionSd(float2 p, float4 region, float radius) {
    float2 halfSize = 0.5 * region.zw;
    float2 q = abs(p - (region.xy + halfSize)) - (halfSize - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

/**
 * The rack a pixel was told to print with, how far off-register to run it, and the bounds its passes
 * may pick ink up from, if any region claims it. False leaves the arguments as they were, for the
 * separation to decide as it always has —
 * which is why they are `inout` and not `out`: an `out` parameter is write-only, and copying one
 * back unwritten would wipe the rows the fan had just worked out for every unclaimed pixel.
 *
 * [reach] is how far outside a region still counts as claimed — zero for a pixel with artwork of its
 * own, and [u_intentReach] for one on bare paper, since the only ink that can reach bare paper is a
 * neighbouring pass wandering off its region. Granting that reach generously is safe: it settles
 * only which rack the pixel would print with, and the caller's probe still decides whether any ink
 * reached it at all.
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
    inout float offsetScale,
    inout float4 clip,
    inout float clipRadius
) {
    bool claimed = false;
    float tightest = 0.0;
    for (int i = 0; i < $capacity; i++) {
        if (float(i) >= u_intentCount) break;

        float4 region = u_intent[i];
        float radius = u_intentSlots[i].w;
        float sd = regionSd(p, region, radius);
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
        clip = region;
        clipRadius = radius;
        claimed = true;
    }
    return claimed;
}

/**
 * Whether a pass may pick ink up from [p]: a region only prints the artwork it contains, so ink
 * thrown past its bounds finds bare paper rather than whatever is drawn next door. An unclaimed
 * pixel is handed bounds big enough to swallow the layer, so this costs it nothing.
 */
bool withinRegion(float2 p, float4 clip, float clipRadius) {
    return regionSd(p, clip, clipRadius) <= 0.0;
}

// No edge feathering, unlike the bypass mask: how much ink lands is still read per pixel from the
// artwork, so the edge of anything drawn inside a region is antialiased by its own coverage. The
// region's own boundary normally sits out where there is nothing printed for it to cut.
""".trimIndent()

/**
 * Sets the ink intent uniforms, clamped to the shader's own [capacity], resolving each region's
 * named inks against the [rack] the press is actually running.
 *
 * ### How a named palette becomes coverage rows
 * The same separation the fan runs, restricted to the drums the region named: [separationRows] gives
 * one row per drum such that `coverage_i = dot(row_i, density)`, so a pixel's own colour still
 * decides how much of each drum it takes. That is what lets one region hold artwork of several
 * colours — a border in one ink, a fill in another — and print each on the drum it was drawn in.
 *
 * A flat fill authored with [risoOverprint][com.alexgabor.design.riso.print.risoOverprint] from
 * these same inks separates back onto them at the coverages it was authored with, since the rows
 * invert exactly the density sum that built it.
 *
 * The basis is the *drum's* ink, not the colour the author passed: naming a colour picks a drum, and
 * the drum's own ink is what prints. Passing a printed appearance — `purple.onRisoPaper()`, which
 * already carries the stock — would otherwise skew the basis by a paper multiply the shader divides
 * out again.
 *
 * A region that names nothing leaves its slots and rows zeroed: no drum runs, and it prints as bare
 * stock.
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

        // A colour prints on at most three drums, as a wedge of the fan does, so a longer list
        // leaves the rest in the rack. Two names landing on one drum would split its coverage
        // between two rows, so they are folded together first.
        val drums = region.inks.take(3).map { slotOf(it, rack) }.distinct()

        // A region that named nothing prints nothing. The zeroed slots matter as much as the zeroed
        // rows: the shader gathers a pass off a slot before it knows the coverage is zero.
        if (drums.isEmpty()) return@repeat

        val subset = separationRows(drums.map { rack[it] })
        drums.forEachIndexed { corner, slot ->
            slots[index * 4 + corner] = slot.toFloat()
            repeat(3) { channel ->
                rows[corner][index * 4 + channel] = subset[corner][channel]
            }
        }
    }

    setFloatUniform("u_intent", bounds)
    setFloatUniform("u_intentSlots", slots)
    setFloatUniform("u_intentRow0", rows[0])
    setFloatUniform("u_intentRow1", rows[1])
    setFloatUniform("u_intentRow2", rows[2])

    // How far a region's claim carries onto the bare paper around it: the furthest any pass on the
    // sheet lands from where it was drawn, so that a blank pixel an amplified region threw ink onto
    // still finds the region that threw it.
    //
    // This is the widest amplifier on screen, and deliberately not per region — a pixel cannot know
    // which region to ask until it has asked. Over-granting it costs nothing, because all a claim
    // settles is which rack the pixel would print with; how far it then looks for artwork, and so
    // whether any ink actually lands, is the claiming region's own amplifier. The feed wobble is not
    // amplified, so it is added after.
    setFloatUniform("u_intentReach", driftPx * widestScale + wobblePx)
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
