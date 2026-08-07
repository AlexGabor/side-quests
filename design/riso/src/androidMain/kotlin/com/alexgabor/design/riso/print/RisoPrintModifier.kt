package com.alexgabor.design.riso.print

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln

/**
 * Re-prints whatever the composable draws as a two-colour Risograph print.
 *
 * The content is separated into two ink coverage maps, each pass is sampled with its own
 * registration error (the pink/blue fringes of a real riso), and the passes are recombined
 * subtractively so overlaps produce a third colour instead of one ink hiding the other.
 *
 * ### Performance
 * Unlike `paperTexture`, nothing can be baked here: the separation depends on the live content, so
 * the whole effect runs per frame. It stays cheap because the per-pixel work is two content samples
 * plus a handful of noise octaves — no voronoi or fold loops.
 */
actual fun Modifier.risoPrint(params: RisoPrintParams): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }

    // Only the *number* of bypassed regions is read here: it fixes the shader's uniform array
    // lengths, so it has to be known at compile time. Where those regions are is read at draw time
    // instead, below.
    val capacity = bypassCapacity(host.peakRegionCount)
    val shader = remember(capacity) { RuntimeShader(risoPrintAgsl(capacity)) }

    val ready = remember(shader, params, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            false
        } else {
            shader.applyRisoParams(params, w.toFloat(), h.toFloat(), density)
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
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "u_image")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    onSizeChanged { size = it }.then(Modifier.risoBypassHost(host)).then(withEffect)
}

/**
 * Sets every uniform the print shader needs. Lengths given in dp by [RisoPrintParams] are converted
 * to pixels here, so the print grain keeps a constant physical size across densities. The
 * composable's content is bound separately as the `u_image` input by the render effect.
 */
private fun RuntimeShader.applyRisoParams(
    params: RisoPrintParams,
    width: Float,
    height: Float,
    density: Float,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_imageSize", width, height)

    val paper = params.paper.transmittance()
    setFloatUniform("u_paper", paper[0], paper[1], paper[2])
    setFloatUniform("u_minTransmittance", MIN_TRANSMITTANCE)

    val inks = params.inks.take(MAX_INKS)
    val separation = separationRows(inks.map { it.color })
    repeat(MAX_INKS) { slot ->
        val ink = inks.getOrNull(slot)
        // Unused drums are given white ink and a zeroed separation row, so they resolve to zero
        // coverage and drop out of both the stacked and the juxtaposed composite.
        val transmittance = ink?.color?.transmittance() ?: floatArrayOf(1f, 1f, 1f)
        setFloatUniform("u_ink$slot", transmittance[0], transmittance[1], transmittance[2])
        setFloatUniform("u_sep$slot", separation.getOrElse(slot) { FloatArray(3) })
        setFloatUniform(
            "u_offset$slot",
            (ink?.offsetX ?: 0f) * density,
            (ink?.offsetY ?: 0f) * density,
        )
        setFloatUniform("u_angle$slot", ((ink?.screenAngle ?: 0f) * PI / 180.0).toFloat())
    }
    setFloatUniform("u_inkCount", inks.size.toFloat())

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

/** The colour as a per-channel transmittance, i.e. what full coverage of it does to white paper. */
private fun Color.transmittance() = floatArrayOf(
    red.coerceIn(MIN_TRANSMITTANCE, 1f),
    green.coerceIn(MIN_TRANSMITTANCE, 1f),
    blue.coerceIn(MIN_TRANSMITTANCE, 1f),
)

/** Optical density of full coverage of [color], i.e. `-ln(transmittance)`. */
private fun densityOf(color: Color): FloatArray {
    val t = color.transmittance()
    return floatArrayOf(-ln(t[0]), -ln(t[1]), -ln(t[2]))
}

private fun dot3(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/**
 * Builds the colour separation as one row vector per ink, such that ink `i`'s coverage at a pixel
 * is `dot(row[i], density)` where `density = -ln(pixel / paper)`.
 *
 * Densities add when inks stack, so recovering the coverages is a least-squares fit of the pixel's
 * density against the ink densities. The rows are the pseudo-inverse of that 3xN system, which
 * depends only on the ink colours and so is solved once here rather than per pixel.
 */
private fun separationRows(inks: List<Color>): List<FloatArray> {
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
 * The print shader, built for [capacity] bypassed regions. See [bypassAgsl] for why the capacity is
 * part of the source rather than a uniform.
 */
internal fun risoPrintAgsl(capacity: Int): String = bypassAgsl(capacity) + "\n" + RISO_PRINT_AGSL

// language=AGSL
internal val RISO_PRINT_AGSL = """
const float PI = 3.14159265359;

uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform shader u_image;

uniform float3 u_paper;
uniform float u_minTransmittance;
uniform float u_inkCount;

uniform float3 u_ink0;
uniform float3 u_ink1;
uniform float3 u_ink2;

// Rows of the density pseudo-inverse: coverage = dot(row, density). See separationRows().
uniform float3 u_sep0;
uniform float3 u_sep1;
uniform float3 u_sep2;

uniform float2 u_offset0;
uniform float2 u_offset1;
uniform float2 u_offset2;
uniform float u_angle0;
uniform float u_angle1;
uniform float u_angle2;

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
 * Ink coverage for one pass, sampled with that pass's registration error.
 * Returns (coverage, source alpha).
 */
float2 inkSample(float2 uv, float2 offsetPx, float3 sepRow, float phase) {
    // Drum feed drifts laterally as the sheet travels, so the error varies down the page.
    float2 off = offsetPx;
    off.x += u_wobble * (0.5 * sin(uv.y * 11.0 + phase) + fbm(float2(uv.y * 5.0, phase)) - 0.5);
    off.y += u_wobble * 0.35 * sin(uv.y * 3.0 + phase * 1.7);

    half4 src = u_image.eval(clamp(uv + off / u_resolution, 0.0, 1.0) * u_imageSize);
    float alpha = float(src.a);
    float3 rgb = alpha > 0.001 ? float3(src.rgb) / alpha : float3(1.0);

    // How much darker than bare paper this pixel is, per channel. Lighter than paper reads as
    // no ink at all — a press cannot print white.
    float3 density = -log(clamp(rgb / u_paper, u_minTransmittance, 1.0));
    return float2(clamp(dot(sepRow, density), 0.0, 1.0) * alpha, alpha);
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

half4 main(float2 fragCoord) {
    // A bypassed region is a window onto the layer: its pixels are fetched 1:1 and handed back
    // untouched, so content that must not be separated survives the press exactly. Well inside one
    // there is no print to compute at all.
    float bypass = bypassMask(fragCoord);
    half4 source = u_image.eval(fragCoord);
    if (bypass >= 1.0) return source;

    float2 uv = fragCoord / u_resolution;

    // Unused drums are skipped rather than sampled: their separation row is zeroed, so they would
    // contribute nothing anyway.
    float2 sample0 = inkSample(uv, u_offset0, u_sep0, u_seed);
    float2 sample1 = float2(0.0);
    float2 sample2 = float2(0.0);
    if (u_inkCount > 1.5) sample1 = inkSample(uv, u_offset1, u_sep1, u_seed + 13.7);
    if (u_inkCount > 2.5) sample2 = inkSample(uv, u_offset2, u_sep2, u_seed + 27.1);

    float c0 = inkPass(sample0.x, fragCoord, u_angle0, u_seed);
    float c1 = inkPass(sample1.x, fragCoord, u_angle1, u_seed + 41.3);
    float c2 = inkPass(sample2.x, fragCoord, u_angle2, u_seed + 63.9);

    // Stacked ink: the passes multiply, so overlaps go dark.
    float3 stacked = u_paper
        * mix(float3(1.0), u_ink0, c0)
        * mix(float3(1.0), u_ink1, c1)
        * mix(float3(1.0), u_ink2, c2);

    // Juxtaposed ink: dots land side by side rather than on top of each other, so an overlap
    // averages the inks instead of subtracting once per pass.
    float total = c0 + c1 + c2;
    float3 juxtaposed = total > 0.001
        ? mix(u_paper, (c0 * u_ink0 + c1 * u_ink1 + c2 * u_ink2) / total, min(total, 1.0))
        : u_paper;

    float3 color = mix(stacked, juxtaposed, u_overprint);
    float opacity = max(sample0.y, max(sample1.y, sample2.y));
    half4 printed = half4(half3(color * opacity), half(opacity));

    // Only the region's antialiased edge reaches here; both sides are premultiplied, so they mix.
    return mix(printed, source, half(bypass));
}
""".trimIndent()
