package com.alexgabor.design.riso.print

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.alexgabor.design.riso.bypass.RisoBypassHost
import com.alexgabor.design.riso.bypass.applyBypass
import com.alexgabor.design.riso.bypass.bypassAgsl
import com.alexgabor.design.riso.bypass.bypassCapacity
import com.alexgabor.design.riso.bypass.risoBypassHost
import com.alexgabor.design.riso.region.regionCapacity
import com.alexgabor.design.riso.separation.RisoInkHost
import com.alexgabor.design.riso.separation.applyInk
import com.alexgabor.design.riso.separation.inkIntentAgsl
import com.alexgabor.design.riso.separation.risoInkHost
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Re-prints whatever the composable draws as a Risograph print.
 *
 * The content is separated into one ink coverage map per drum, each pass is sampled with its own
 * registration error (the pink/blue fringes of a real riso), and the passes are recombined
 * subtractively so overlaps produce a new colour instead of one ink hiding the other.
 *
 * ### Performance
 * The sheet is static for a given stock and layout, so its surface is **baked once** into a cached
 * texture (see [paperMapShader]) and shared across every usage with the same key; changing an ink
 * never re-bakes it. The separation cannot be baked — it depends on the live content — so that half
 * runs per frame. Every loaded drum reads the artwork under its own registration error, so the rack
 * costs one content sample per drum; what it does *not* cost is a noise field each, because a pass
 * that read bare paper stops there. Most of a screen is paper or flat artwork and prints on one or
 * two drums, so the screening and mottling — which is where the real work is — stays flat as the
 * rack grows.
 */
actual fun Modifier.risoPrint(params: RisoPrintParams): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }
    val inkHost = remember { RisoInkHost() }

    // Only the *number* of regions is read here: it fixes the shader's uniform array lengths, so it
    // has to be known at compile time. Where those regions are, and what they say, is read at draw
    // time instead, below.
    val capacity = bypassCapacity(host.peakRegionCount)
    val intentCapacity = regionCapacity(inkHost.peakRegionCount)
    // The drum count sizes uniform arrays the same way, so it is bucketed too: loading or pulling a
    // drum in a picker mostly reuses the shader it already compiled.
    val inkCapacity = inkCapacity(params.inks.size)
    val shader = remember(capacity, intentCapacity, inkCapacity) {
        RuntimeShader(risoPrintAgsl(capacity, intentCapacity, inkCapacity))
    }

    // Keyed on the stock alone, so loading a drum or dragging an ink slider never re-bakes the
    // sheet — only a new paper or a new layout size does.
    val paperMap = remember(params.paper, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) null else paperMapShader(params.paper, w, h, density)
    }

    val ready = remember(shader, params, size, density, paperMap) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0 || paperMap == null) {
            false
        } else {
            shader.applyRisoParams(params, inkCapacity, w.toFloat(), h.toFloat(), density, paperMap)
            true
        }
    }

    // The render effect is rebuilt in the draw block rather than in composition, because a
    // RenderEffect bakes in its shader's uniforms and the bypass bounds move with every layout
    // pass. Going through a recomposition would land them a frame late, and a bypassed child would
    // visibly trail its own window on a fling.
    val withEffect = if (ready) {
        Modifier.graphicsLayer {
            clip = true
            shader.applyBypass(host.regions, capacity)
            shader.applyInk(
                regions = inkHost.regions,
                capacity = intentCapacity,
                rack = params.inks.map { it.color },
                driftPx = params.registrationDrift * density,
                wobblePx = abs(params.wobble) * density,
            )
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "u_image")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    onSizeChanged { size = it }
        .then(Modifier.risoBypassHost(host))
        .then(Modifier.risoInkHost(inkHost))
        .then(withEffect)
}

/**
 * Sets every uniform the print shader needs. Lengths given in dp by [RisoPrintParams] are converted
 * to pixels here, so the print grain keeps a constant physical size across densities. The
 * composable's content is bound separately as the `u_image` input by the render effect.
 */
private fun RuntimeShader.applyRisoParams(
    params: RisoPrintParams,
    capacity: Int,
    width: Float,
    height: Float,
    density: Float,
    paperMap: Shader,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_imageSize", width, height)

    val paper = params.paper
    // colorFront's RGB is also the white point the separation works against; its alpha only says
    // whether the sheet gets painted.
    val reference = paper.colorFront.transmittance()
    setFloatUniform("u_paper", reference[0], reference[1], reference[2])
    setFloatUniform("u_minTransmittance", MIN_TRANSMITTANCE)

    setColorComponents("u_colorFront", paper.colorFront)
    setColorComponents("u_colorBack", paper.colorBack)
    setFloatUniform("u_paperWarp", if (paper.warps) 1f else 0f)
    // The tolerance is authored as a fraction darker than the stock; the shader wants the density
    // that corresponds to, since that is what it subtracts.
    setFloatUniform("u_paperFloor", -ln(1f - paper.tolerance.coerceIn(0f, 0.5f)))
    setInputShader("u_paperMap", paperMap)

    // Drums past what the compiled shader can hold are left in the rack. The clamp only bites in
    // the frame between the palette growing past a bucket and the wider shader landing.
    val inks = params.inks.take(capacity)
    val separation = separationRows(inks.map { it.color })
    val fan = separationFan(inks.map { it.color })

    // Each drum is one float4 slot per array: the vec3s are padded out to float4 so the uniform
    // sizes are unambiguous, and slots past the count stay zeroed — the shader's loop stops first.
    val inkUniform = FloatArray(capacity * 4)
    val sepUniform = FloatArray(capacity * 4)
    val passUniform = FloatArray(capacity * 4)
    inks.forEachIndexed { slot, ink ->
        val transmittance = ink.color.transmittance()
        val row = separation.getOrElse(slot) { FloatArray(3) }
        repeat(3) { channel ->
            inkUniform[slot * 4 + channel] = transmittance[channel]
            sepUniform[slot * 4 + channel] = row[channel]
        }
        passUniform[slot * 4] = ink.offsetX * density
        passUniform[slot * 4 + 1] = ink.offsetY * density
        passUniform[slot * 4 + 2] = (ink.screenAngle * PI / 180.0).toFloat()
    }
    setFloatUniform("u_ink", inkUniform)
    setFloatUniform("u_sep", sepUniform)
    setFloatUniform("u_pass", passUniform)
    setFloatUniform("u_inkCount", inks.size.toFloat())

    // One float4 slot per wedge as well: the three drums it prints with and where it starts, beside
    // the three rows that give those drums their coverage. A rack with no fan leaves the count at
    // zero, and the shader falls back to the least-squares rows above.
    val wedgeUniform = FloatArray(capacity * 4)
    val rowUniform = Array(3) { FloatArray(capacity * 4) }
    fan?.wedges?.forEachIndexed { index, wedge ->
        repeat(3) { corner ->
            wedgeUniform[index * 4 + corner] = wedge.slots[corner].toFloat()
            repeat(3) { channel ->
                rowUniform[corner][index * 4 + channel] = wedge.rows[corner][channel]
            }
        }
        wedgeUniform[index * 4 + 3] = wedge.startAngle
    }
    setFloatUniform("u_wedge", wedgeUniform)
    setFloatUniform("u_wedgeRow0", rowUniform[0])
    setFloatUniform("u_wedgeRow1", rowUniform[1])
    setFloatUniform("u_wedgeRow2", rowUniform[2])
    setFloatUniform("u_wedgeCount", (fan?.wedges?.size ?: 0).toFloat())
    setFloatUniform("u_fanAnchor", fan?.anchor?.get(0) ?: 0f, fan?.anchor?.get(1) ?: 0f)
    setFloatUniform("u_fanBase", fan?.base ?: 0f)

    setFloatUniform("u_overprint", params.overprint.coerceIn(0f, 1f))
    setFloatUniform("u_screen", params.screen.coerceIn(0f, 1f))
    setFloatUniform("u_dotSize", (params.dotSize * density).coerceAtLeast(1.5f))
    setFloatUniform("u_mottle", params.mottle.coerceIn(0f, 1f))
    setFloatUniform("u_mottleSize", (params.mottleSize * density).coerceAtLeast(1f))
    setFloatUniform("u_grain", params.grain.coerceIn(0f, 1f))
    setFloatUniform("u_grainSize", (params.grainSize * density).coerceAtLeast(1f))
    setFloatUniform("u_wobble", params.wobble * density)
    setFloatUniform("u_spread", params.spread.coerceIn(0f, 1f))
    setFloatUniform("u_seed", params.seed)
}

/**
 * How far the worst-registered pass on the rack lands from where it was drawn, in dp, before any
 * region amplifies it. [applyInk] scales it by the widest amplifier on screen to decide how far a
 * region's claim carries past its own bounds — a pixel on the bare paper around a region has to be
 * able to find the region whose ink drifted onto it.
 */
internal val RisoPrintParams.registrationDrift: Float
    get() = inks.maxOfOrNull { maxOf(abs(it.offsetX), abs(it.offsetY)) } ?: 0f

/** The colour as a per-channel transmittance, i.e. what full coverage of it does to white paper. */
internal fun Color.transmittance() = floatArrayOf(
    red.coerceIn(MIN_TRANSMITTANCE, 1f),
    green.coerceIn(MIN_TRANSMITTANCE, 1f),
    blue.coerceIn(MIN_TRANSMITTANCE, 1f),
)

/** Optical density of full coverage of [color], i.e. `-ln(transmittance)`. */
internal fun densityOf(color: Color): FloatArray {
    val t = color.transmittance()
    return floatArrayOf(-ln(t[0]), -ln(t[1]), -ln(t[2]))
}

internal fun dot3(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/**
 * Builds the colour separation as one row vector per ink, such that ink `i`'s coverage at a pixel
 * is `dot(row[i], density)` where `density = -ln(pixel / paper)`.
 *
 * Densities add when inks stack, so recovering the coverages is a least-squares fit of the pixel's
 * density against the ink densities. The rows are the pseudo-inverse of that 3xN system, which
 * depends only on the ink colours and so is solved once here rather than per pixel.
 */
internal fun separationRows(inks: List<Color>): List<FloatArray> {
    if (inks.isEmpty()) return emptyList()
    val densities = inks.map(::densityOf)
    val n = densities.size

    // Normal equations of the fit, D^T D.
    val normal = Array(n) { i -> FloatArray(n) { j -> dot3(densities[i], densities[j]) } }

    // A ridge term proportional to the system's own scale, so that near-collinear inks (two blues,
    // say) share coverage between them instead of the inverse blowing up. It is small enough that
    // a well-separated palette still round-trips to within half a percent.
    var trace = 0f
    repeat(n) { trace += normal[it][it] }
    val ridge = 1e-3f * trace / n
    repeat(n) { normal[it][it] += ridge }

    val inverse = invert(normal) ?: return inks.map { FloatArray(3) }
    return List(n) { i ->
        FloatArray(3) { channel ->
            var sum = 0f
            repeat(n) { j -> sum += inverse[i][j] * densities[j][channel] }
            sum
        }
    }
}

/**
 * One wedge of the ink cone: the three drums that print any colour falling in it, the rows that give
 * those drums their coverage, and the hue the wedge starts at.
 */
private class SeparationWedge(
    val slots: IntArray,
    val rows: Array<FloatArray>,
    val startAngle: Float,
)

/** The whole fan: where it is centred, the hue its first wedge starts at, and the wedges. */
private class SeparationFan(
    val anchor: FloatArray,
    val base: Float,
    val wedges: List<SeparationWedge>,
)

/**
 * Decomposes the rack into wedges, so that a colour can be separated onto the few drums it actually
 * needs instead of a thin wash across every drum loaded.
 *
 * A least-squares fit against many inks is underdetermined — the ink densities span three dimensions
 * however many drums there are — and its minimum-norm answer prefers to spread a colour over the
 * whole rack, because many small coverages have a smaller norm than one large one. Printing black
 * then means running all twelve drums, and a colour authored with [risoOverprint] does not separate
 * back into the inks it was authored from.
 *
 * A press does not work that way: it picks the drums the colour needs. So the ink densities are
 * sorted by hue around the most achromatic of them and taken in neighbouring pairs, each pair
 * forming a wedge with the anchor. Every wedge is a 3x3 that inverts exactly, so a colour is
 * separated onto at most three drums with no residual — and a colour that sits on the seam between
 * two wedges, which is every tint of a single ink and every overprint of the anchor with one other,
 * round-trips exactly onto the drums it was authored from.
 *
 * Returns null for a rack too small or too collinear to fan out, leaving [separationRows] to it.
 */
private fun separationFan(inks: List<Color>): SeparationFan? {
    if (inks.size < 3) return null
    val densities = inks.map(::densityOf)

    // The anchor carries the achromatic weight of every wedge, so it is the ink closest to grey —
    // the black drum on a normal rack.
    val anchor = densities.indices.minBy { chromaOf(densities[it]) }
    val anchorPoint = chromaticity(densities[anchor])

    val fan = densities.indices
        .filter { it != anchor }
        .map { it to angleFrom(anchorPoint, chromaticity(densities[it])) }
        .sortedBy { it.second }
    val base = fan.first().second

    val wedges = mutableListOf<SeparationWedge>()
    var index = 0
    while (index < fan.size) {
        val (startSlot, startAngle) = fan[index]

        // Two inks of the same hue leave a wedge with no width and a singular matrix; step over them
        // until the wedge has some volume, so the fan comes out gap-free. Inks skipped this way are
        // metamers of the one before them and simply never get loaded.
        var span = 1
        var inverse: Array<FloatArray>? = null
        var endSlot = startSlot
        while (span <= fan.size) {
            endSlot = fan[(index + span) % fan.size].first
            inverse = invert(
                Array(3) { channel ->
                    floatArrayOf(
                        densities[anchor][channel],
                        densities[startSlot][channel],
                        densities[endSlot][channel],
                    )
                },
            )
            if (inverse != null) break
            span++
        }
        if (inverse == null) return null

        wedges += SeparationWedge(
            slots = intArrayOf(anchor, startSlot, endSlot),
            rows = inverse,
            startAngle = startAngle - base,
        )
        index += span
    }
    return SeparationFan(anchorPoint, base, wedges)
}

/** Where a density sits on the plane of hues, i.e. its three channels normalised to sum to one. */
private fun chromaticity(density: FloatArray): FloatArray {
    val total = density[0] + density[1] + density[2]
    if (total <= 1e-6f) return floatArrayOf(1f / 3f, 1f / 3f)
    return floatArrayOf(density[0] / total, density[1] / total)
}

/** How far off the achromatic axis a density sits, relative to how dark it is. */
private fun chromaOf(density: FloatArray): Float {
    val mean = (density[0] + density[1] + density[2]) / 3f
    if (mean <= 1e-6f) return 0f
    var sum = 0f
    repeat(3) { channel ->
        val delta = density[channel] - mean
        sum += delta * delta
    }
    return sqrt(sum) / mean
}

/** The hue of [point] as seen from [anchor], in radians. */
private fun angleFrom(anchor: FloatArray, point: FloatArray) =
    atan2(point[1] - anchor[1], point[0] - anchor[0])

/** Gauss-Jordan inverse of a small square matrix, or null if it is singular. */
private fun invert(matrix: Array<FloatArray>): Array<FloatArray>? {
    val n = matrix.size
    // Augment with the identity and reduce the left half to it.
    val work = Array(n) { i ->
        FloatArray(2 * n).also { row ->
            matrix[i].copyInto(row)
            row[n + i] = 1f
        }
    }
    for (col in 0 until n) {
        var pivot = col
        for (row in col + 1 until n) {
            if (abs(work[row][col]) > abs(work[pivot][col])) pivot = row
        }
        if (abs(work[pivot][col]) < 1e-6f) return null
        work[col] = work[pivot].also { work[pivot] = work[col] }

        val scale = work[col][col]
        for (k in 0 until 2 * n) work[col][k] /= scale
        for (row in 0 until n) {
            if (row == col) continue
            val factor = work[row][col]
            if (factor != 0f) {
                for (k in 0 until 2 * n) work[row][k] -= factor * work[col][k]
            }
        }
    }
    return Array(n) { i -> FloatArray(n) { j -> work[i][n + j] } }
}

/**
 * Uniform-array capacity for [count] drums, rounded up to a multiple of four. Bucketing keeps a
 * picker that loads and pulls drums from recompiling the shader on every change, and the step is
 * kept small because every unused slot is uniform storage the shader pays for whether it runs a
 * drum or not.
 */
internal fun inkCapacity(count: Int): Int =
    MIN_INK_CAPACITY * ((maxOf(count, 1) + MIN_INK_CAPACITY - 1) / MIN_INK_CAPACITY)

private const val MIN_INK_CAPACITY = 4

/**
 * The print shader, built for [capacity] bypassed regions, [intentCapacity] regions naming their own
 * drums, and [inkCapacity] drums. See [bypassAgsl] for why the capacities are part of the source
 * rather than uniforms.
 */
internal fun risoPrintAgsl(capacity: Int, intentCapacity: Int, inkCapacity: Int): String =
    bypassAgsl(capacity) + "\n" + inkIntentAgsl(intentCapacity) + "\n" +
        risoPrintBodyAgsl(inkCapacity)


// language=AGSL
internal fun risoPrintBodyAgsl(inkCapacity: Int): String = """
const float PI = 3.14159265359;

/**
 * Coverage below which a drum is not worth screening. A little over one percent of an ink moves a
 * channel by about two levels out of 255 — less than its own grain would — so the ink is laid flat
 * rather than put through the screen and the mottle.
 */
const float FAINT_COVERAGE = 0.012;

/** Density below which a sample is bare paper, and the drum that read it has nothing to lay down. */
const float BARE_DENSITY = 0.0005;

uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform shader u_image;

uniform float3 u_paper;
uniform float u_minTransmittance;
uniform float u_inkCount;

// One float4 slot per drum. xyz of u_ink is the ink's transmittance; xyz of u_sep is its row of the
// density pseudo-inverse, so coverage = dot(row, density) — see separationRows(). u_pass carries
// that drum's registration error in xy (pixels) and its screen angle in z (radians).
uniform float4 u_ink[$inkCapacity];
uniform float4 u_sep[$inkCapacity];
uniform float4 u_pass[$inkCapacity];

// One float4 slot per wedge of the ink cone: the three drums it prints with in xyz, and the hue it
// starts at in w. u_wedgeRow0..2 are the rows that give those three drums their coverage. Wedges
// ascend from zero around u_fanAnchor, starting at u_fanBase. See separationFan().
uniform float4 u_wedge[$inkCapacity];
uniform float4 u_wedgeRow0[$inkCapacity];
uniform float4 u_wedgeRow1[$inkCapacity];
uniform float4 u_wedgeRow2[$inkCapacity];
uniform float u_wedgeCount;
uniform float2 u_fanAnchor;
uniform float u_fanBase;

uniform float u_overprint;
uniform float u_screen;
uniform float u_dotSize;
uniform float u_mottle;
uniform float u_mottleSize;
uniform float u_grain;
uniform float u_grainSize;
uniform float u_wobble;
uniform float u_spread;
uniform float u_seed;

// The sheet, baked once per stock and layout by RisoPaperBake.kt: rg is how far its surface pushes
// the artwork around (encoded * 0.25 + 0.5), b is how the light falls on it (encoded * 0.5 + 0.5).
uniform shader u_paperMap;
uniform float4 u_colorFront;
uniform float4 u_colorBack;
// A stock with no surface to speak of neither pushes the artwork around nor needs the edge of the
// displaced content antialiased — and the map's 8-bit encoding cannot represent "no push" exactly,
// so it is switched off here rather than left to round to zero.
uniform float u_paperWarp;
// Density below which a pixel is simply the stock, and comes off the press unprinted.
uniform float u_paperFloor;

float2 rotate(float2 p, float th) {
    float s = sin(th);
    float c = cos(th);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float hash(float2 p) {
    float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
    q += dot(q, float3(q.y, q.z, q.x) + 33.33);
    return fract((q.x + q.y) * q.z);
}

float valueNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

/** Value-noise fbm, normalised to 0..1. Octaves are rotated so blotches don't read as a grid. */
float fbm(float2 p) {
    float total = 0.0;
    float amplitude = 0.6;
    for (int i = 0; i < 3; i++) {
        total += amplitude * valueNoise(p);
        p = rotate(p, 0.73) * 2.17 + 11.3;
        amplitude *= 0.5;
    }
    return total / 1.05;
}

/**
 * How much darker than bare paper a colour is, per channel. Lighter than paper reads as no ink at
 * all — a press cannot print white — and so does anything within u_paperFloor of the stock, which
 * is what keeps artwork authored to the paper colour from separating into a tint no press could
 * hold. The floor is subtracted rather than thresholded, so ink fades in instead of switching on.
 */
float3 densityOfRgb(float3 rgb) {
    return max(-log(clamp(rgb / u_paper, u_minTransmittance, 1.0)) - u_paperFloor, 0.0);
}

/**
 * The content under one pass, sampled with that pass's registration error and the sheet's own
 * displacement. Returns (optical density, source alpha).
 *
 * [clip] bounds the artwork this pass is entitled to: a region prints what it contains, so a pass
 * thrown past its edge comes back with nothing rather than picking up whatever was drawn next door.
 * An unclaimed pixel is handed bounds larger than the layer, so the test never bites.
 */
float4 inkSample(
    float2 uv,
    float2 warp,
    float2 offsetPx,
    float phase,
    float feed,
    float4 clip,
    float clipRadius
) {
    // Drum feed drifts laterally as the sheet travels, so the error varies down the page. [feed] is
    // the sheet's share of that — one noise field for the whole press, read once per pixel by the
    // caller, because it is the paper travelling unevenly and not each drum inventing its own. What
    // is per drum is the periodic error of its own rotation, which is a sine and costs nothing.
    float2 off = offsetPx;
    off.x += u_wobble * (0.5 * sin(uv.y * 11.0 + phase) + feed - 0.5);
    off.y += u_wobble * 0.35 * sin(uv.y * 3.0 + phase * 1.7);

    // A pass thrown off the sheet finds nothing, rather than the layer's last row of pixels smeared
    // across a band as wide as the offset — which is what clamping the read into range would do.
    float2 sheetUv = uv + warp + off / u_resolution;
    if (sheetUv.x < 0.0 || sheetUv.x > 1.0 || sheetUv.y < 0.0 || sheetUv.y > 1.0) {
        return float4(0.0);
    }
    float2 at = sheetUv * u_imageSize;

    // Only the registration error is asked to stay inside the region, since that is the one this
    // region amplifies. The sheet's warp and the drum's feed wobble carry the region along with the
    // artwork printed on it, so neither counts against its bounds — testing them too would starve a
    // band the width of the displacement along every region's edge, which reads as a hairline drawn
    // around the region.
    if (!withinRegion((uv + offsetPx / u_resolution) * u_imageSize, clip, clipRadius)) {
        return float4(0.0);
    }

    half4 src = u_image.eval(at);
    float alpha = float(src.a);
    float3 rgb = alpha > 0.001 ? float3(src.rgb) / alpha : float3(1.0);

    return float4(densityOfRgb(rgb), alpha);
}

/** How much darker than bare paper a sample is. */
float3 densityOf(half4 src) {
    float alpha = float(src.a);
    float3 rgb = alpha > 0.001 ? float3(src.rgb) / alpha : float3(1.0);
    return densityOfRgb(rgb);
}

float weightOf(float3 density) {
    return density.x + density.y + density.z;
}

/**
 * Which wedge of the rack a colour falls in, as the three drums that print it in [slots] and their
 * three coverage rows. See separationFan().
 */
void selectWedge(float3 density, out float3 slots, out float3 row0, out float3 row1, out float3 row2) {
    slots = u_wedge[0].xyz;
    row0 = u_wedgeRow0[0].xyz;
    row1 = u_wedgeRow1[0].xyz;
    row2 = u_wedgeRow2[0].xyz;

    float total = weightOf(density);
    if (total <= 0.0001) return;

    float2 hue = density.xy / total - u_fanAnchor;
    float angle = mod(atan(hue.y, hue.x) - u_fanBase, 2.0 * PI);

    // Wedges ascend from zero, so the last one starting at or before this hue is the one it is in —
    // and the first one starting after it ends the search.
    for (int w = 1; w < $inkCapacity; w++) {
        if (float(w) >= u_wedgeCount) break;
        if (u_wedge[w].w > angle) break;
        slots = u_wedge[w].xyz;
        row0 = u_wedgeRow0[w].xyz;
        row1 = u_wedgeRow1[w].xyz;
        row2 = u_wedgeRow2[w].xyz;
    }
}

/**
 * The row that separates drum [slot] out of [density], or zero if the wedge that colour falls in
 * does not print on that drum at all.
 *
 * This is what lets a drum be separated against the artwork *it* read rather than against whatever
 * happens to sit under the pixel being printed. The two are the same colour only while the
 * registration error stays under a pixel; past that, a row fitted to one colour is being asked to
 * decompose another, and returns a coverage that means nothing.
 */
float3 rowForSlot(int slot, float3 density) {
    float3 slots;
    float3 row0;
    float3 row1;
    float3 row2;
    selectWedge(density, slots, row0, row1, row2);

    float s = float(slot);
    if (s == slots.x) return row0;
    if (s == slots.y) return row1;
    if (s == slots.z) return row2;
    return float3(0.0);
}

/** The blotchiness and speckle of ink actually laid down on paper. */
float inkTexture(float coverage, float2 fragCoord, float phase) {
    if (coverage <= 0.0) return 0.0;
    coverage *= 1.0 - u_mottle * (1.0 - fbm(fragCoord / u_mottleSize + phase));
    coverage *= 1.0 - u_grain * hash(floor(fragCoord / u_grainSize) + phase);
    return clamp(coverage, 0.0, 1.0);
}

/** Thresholds coverage against a rotated dot screen, so tone becomes dots of varying size. */
float screenDots(float coverage, float2 fragCoord, float angle) {
    if (u_screen <= 0.0) return coverage;
    float2 p = rotate(fragCoord, angle) * (PI / u_dotSize);
    float field = 0.5 - 0.5 * cos(p.x) * cos(p.y);
    // Soften the threshold, and stretch coverage past the field's 0..1 range so that full
    // coverage fills the cell corners solid instead of leaving holes.
    float w = clamp(2.0 / u_dotSize, 0.06, 0.45);
    float t = coverage * (1.0 + 2.0 * w) - w;
    return mix(coverage, clamp((t - field) / w + 0.5, 0.0, 1.0), u_screen);
}

/**
 * One drum end to end: ink gain first, so dots grow the way ink spreads once it hits the paper,
 * then the screen, then blotching last — otherwise the grain punches holes through the dots
 * instead of mottling a solid.
 */
float inkPass(float coverage, float2 fragCoord, float angle, float phase) {
    if (coverage <= 0.0) return 0.0;
    coverage = pow(coverage, 1.0 / (1.0 + u_spread));
    coverage = screenDots(coverage, fragCoord, angle);
    return inkTexture(coverage, fragCoord, phase);
}

/**
 * Antialiases the edge of the content once the sheet has pushed it around, so a read clamped to the
 * layer's edge does not smear its last row of pixels across the margin.
 */
float getUvFrame(float2 uv) {
    float aax = 2.0 / u_resolution.x;
    float aay = 2.0 / u_resolution.y;
    float left = smoothstep(0.0, aax, uv.x);
    float right = 1.0 - smoothstep(1.0 - aax, 1.0, uv.x);
    float bottom = smoothstep(0.0, aay, uv.y);
    float top = 1.0 - smoothstep(1.0 - aay, 1.0, uv.y);
    return left * right * bottom * top;
}

/**
 * Lays a printed pixel down on the sheet: the stock seen through the ink, plus the ink itself
 * wherever the stock is not painted, so an unpainted sheet hands the print back exactly as it was.
 *
 * [transmittance] is what the ink does to the light coming off the sheet — 1 is bare paper, which
 * leaves the stock untouched whether the artwork there was opaque or transparent. That is the whole
 * reason the sheet multiplies rather than being blended over: paper is paper either way.
 */
half4 onSheet(float3 transmittance, float3 straight, float cover, float3 sheet, float sheetOpacity) {
    float3 color = sheet * mix(float3(1.0), transmittance, cover);
    color += straight * cover * (1.0 - sheetOpacity);
    return half4(half3(color), half(sheetOpacity + cover * (1.0 - sheetOpacity)));
}

/** Source-over, for a bypassed window: content untouched, with the sheet showing through it. */
half4 overSheet(half4 content, float frame, float3 sheet, float sheetOpacity) {
    float a = float(content.a) * frame;
    float3 c = float3(content.rgb) * frame;
    return half4(half3(c + sheet * (1.0 - a)), half(a + sheetOpacity * (1.0 - a)));
}

half4 main(float2 fragCoord) {
    // The sheet, baked once per stock and layout: how far its surface pushes the artwork around,
    // and how the light falls on it.
    half4 baked = u_paperMap.eval(fragCoord);
    float2 surface = (float2(baked.r, baked.g) - 0.5) / 0.25;
    float res = clamp(float(baked.b) * 2.0 - 1.0, 0.0, 1.0);

    // The stock itself: its lit front over whatever shows through it. The lighting works on the
    // front's opacity, so a default sheet still takes most of its colour from the back.
    float3 sheet = u_colorFront.rgb * u_colorFront.a * res;
    float sheetOpacity = u_colorFront.a * res;
    sheet += u_colorBack.rgb * u_colorBack.a * (1.0 - sheetOpacity);
    sheetOpacity += u_colorBack.a * (1.0 - sheetOpacity);

    // A bypassed region is a window onto the layer: the sheet stops acting on the content there —
    // it is neither pushed around by the surface, nor shaded by it, nor separated — so its pixels
    // arrive exactly as drawn. The stock is still painted behind it, which is what shows through
    // anything translucent.
    float bypass = bypassMask(fragCoord);

    float2 uv = fragCoord / u_resolution;
    float2 warp = u_paperWarp * 0.02 * surface * (1.0 - bypass);
    // Carried in pixels for the reads that take pixels, rather than round-tripping through
    // uv * u_imageSize, which is not exact and would shift a flat stock by a texel.
    float2 warpPx = warp * u_imageSize;
    float frame = mix(1.0, getUvFrame(uv + warp), u_paperWarp);

    // How unevenly the sheet is travelling through the press at this point down the page. Every pass
    // is carried by the same sheet, so this is read once and handed to all of them — see inkSample().
    float feed = valueNoise(float2(uv.y * 5.0, u_seed));

    half4 source = u_image.eval(clamp(fragCoord + warpPx, float2(0.0), u_imageSize));
    if (bypass >= 1.0) return overSheet(source, frame, sheet, sheetOpacity);

    float opacity = float(source.a);

    // Whether the pixel has artwork of its own, which is all the region claim below needs it for.
    bool bare = weightOf(densityOf(source)) <= BARE_DENSITY;

    float3 slots = float3(0.0);
    float3 row0 = float3(0.0);
    float3 row1 = float3(0.0);
    float3 row2 = float3(0.0);
    float offsetScale = 1.0;
    // Bounds a pass may pick ink up from. Unclaimed, that is the whole sheet and more, so the test
    // inside inkSample() never bites; a region narrows it to itself.
    float4 clip = float4(-u_imageSize, 3.0 * u_imageSize);
    float clipRadius = 0.0;

    // Whether a region claims this pixel is settled first, because how far the pixel has to look for
    // the artwork drifting onto it is the claiming region's business: a region that amplifies its
    // registration throws its passes further than the rack alone would.
    //
    // A region that names its own drums also outranks the fan's read of the colour, and brings its
    // own rows, so it needs no fan at all — which is what lets a two-drum press honour an intent the
    // cone is too small to describe. On bare paper the claim reaches as far as any pass on the sheet
    // can drift, because the only ink that lands there came from a region it drifted out of.
    bool claimed = selectIntent(
        fragCoord, bare ? u_intentReach : 0.0,
        slots, row0, row1, row2, offsetScale, clip, clipRadius);

    // Stacked ink: the passes multiply, so overlaps go dark.
    float3 stacked = u_paper;
    // Juxtaposed ink: dots land side by side rather than on top of each other, so an overlap
    // averages the inks instead of subtracting once per pass. Accumulated as a coverage-weighted
    // sum of the inks, divided through once the run of drums is done.
    float3 mixed = float3(0.0);
    float total = 0.0;

    // Every drum reads for itself. A pass lays down what it finds under its own registration error,
    // separated against *that* colour — which is the whole difference between a print that survives
    // being thrown off register and one that does not. Deciding the rack once from the colour under
    // the pixel is only the same answer while the error stays inside a pixel; past that, each drum
    // is looking at different artwork, and asking one colour's row to decompose another's density
    // gives a coverage that means nothing.
    //
    // So there is no probe and no early out for bare paper: whether anything prints here is settled
    // by the passes themselves, and a pixel no pass reaches accumulates nothing and comes off the
    // press as stock.
    for (int i = 0; i < $inkCapacity; i++) {
        if (float(i) >= u_inkCount) break;

        // Exaggerate — or cancel — this region's misregistration. Only the drum's own error is
        // scaled: the feed wobble added inside inkSample() varies down the page, so scaling that
        // per region would tear along the region's edge where a constant offset does not.
        float4 pass = u_pass[i];
        pass.xy *= offsetScale;

        // A region names the drums it prints with and brings its own rows, which are authored
        // against the inks rather than fitted to a colour — so they hold wherever the pass landed,
        // and a drum the region did not name simply does not run.
        float3 row = float3(0.0);
        if (claimed) {
            if (float(i) == slots.x) row = row0;
            else if (float(i) == slots.y) row = row1;
            else if (float(i) == slots.z) row = row2;
            else continue;
        }

        float phase = u_seed + float(i) * 13.7;
        float4 sampled = inkSample(uv, warp, pass.xy, phase, feed, clip, clipRadius);
        // Nothing under this pass. Bailing here is what keeps a full rack affordable: most of a
        // sheet is paper, and a drum that read paper costs one sample and no noise field at all.
        if (weightOf(sampled.xyz) <= BARE_DENSITY) continue;

        if (!claimed) {
            // No region spoke for the pixel, so the fan reads the colour this pass found and hands
            // back the row for this drum — zero if the wedge that colour falls in does not print on
            // it. A rack too small or too collinear to fan out has no wedges, and every drum runs
            // off its own least-squares row instead.
            row = u_wedgeCount >= 1.0 ? rowForSlot(i, sampled.xyz) : u_sep[i].xyz;
        }

        float laid = clamp(dot(row, sampled.xyz), 0.0, 1.0) * sampled.w;
        if (laid <= 0.0) continue;

        // A drum the colour barely calls for still reaches the sheet, laid flat — but screening and
        // mottling a coverage this faint is work the print cannot show. Laying the ink down rather
        // than dropping it keeps the colour continuous, so nothing pops as a drum crosses the
        // threshold. Each drum that does run lays down its own noise field, so the mottling of one
        // pass does not line up with the next.
        float cover = laid > FAINT_COVERAGE
            ? inkPass(laid, fragCoord, pass.z, phase + 41.3)
            : laid;

        float3 ink = u_ink[i].xyz;
        stacked *= mix(float3(1.0), ink, cover);
        mixed += cover * ink;
        total += cover;
        opacity = max(opacity, sampled.w);
    }

    float3 juxtaposed = total > 0.001
        ? mix(u_paper, mixed / total, min(total, 1.0))
        : u_paper;

    float3 straight = mix(stacked, juxtaposed, u_overprint);
    // What the ink laid down does to the light coming off the stock. Bare paper divides out to 1
    // and leaves the sheet alone, which is why there is no seam where artwork stops.
    float3 transmittance = clamp(straight / u_paper, 0.0, 1.0);

    // Only the region's antialiased edge reaches here; both sides are premultiplied, so they mix.
    return mix(
        onSheet(transmittance, straight, opacity * frame, sheet, sheetOpacity),
        overSheet(source, frame, sheet, sheetOpacity),
        half(bypass));
}
""".trimIndent()
