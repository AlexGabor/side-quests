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
import kotlin.math.PI
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
    val shader = remember { RuntimeShader(RISO_PRINT_AGSL) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val effect = remember(params, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            null
        } else {
            shader.applyRisoParams(params, w.toFloat(), h.toFloat(), density)
            RenderEffect
                .createRuntimeShaderEffect(shader, "u_image")
                .asComposeRenderEffect()
        }
    }

    val withEffect = if (effect != null) {
        Modifier.graphicsLayer(renderEffect = effect, clip = true)
    } else {
        Modifier
    }

    onSizeChanged { size = it }.then(withEffect)
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
    setInkUniforms("A", params.inkA, density)
    setInkUniforms("B", params.inkB, density)

    val (sepA, sepB) = separationRows(params.inkA.color, params.inkB.color)
    setFloatUniform("u_sepA", sepA)
    setFloatUniform("u_sepB", sepB)

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

private fun RuntimeShader.setInkUniforms(suffix: String, ink: RisoInk, density: Float) {
    val t = ink.color.transmittance()
    setFloatUniform("u_ink$suffix", t[0], t[1], t[2])
    setFloatUniform("u_offset$suffix", ink.offsetX * density, ink.offsetY * density)
    setFloatUniform("u_angle$suffix", (ink.screenAngle * PI / 180.0).toFloat())
}

/**
 * Lower bound on a transmittance. A channel that transmits nothing has infinite density, so pure
 * black is clamped to a very dark — but finite — ink. The shader floors pixel transmittance at the
 * same value (`u_minTransmittance`); if the two disagreed, the darkest colours would separate into
 * more ink than the inks themselves can lay down.
 */
private const val MIN_TRANSMITTANCE = 0.02f

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
 * Builds the colour separation as two row vectors, such that a pixel's ink coverages are
 * `cA = dot(rowA, density)` and `cB = dot(rowB, density)` where `density = -ln(pixel / paper)`.
 *
 * Densities add when inks stack, so recovering the coverages is a least-squares fit of the pixel's
 * density against the two ink densities — the rows are the pseudo-inverse of that 3x2 system,
 * which only depends on the ink colours and so is solved once here rather than per pixel.
 */
private fun separationRows(inkA: Color, inkB: Color): Pair<FloatArray, FloatArray> {
    val dA = densityOf(inkA)
    val dB = densityOf(inkB)
    val aa = dot3(dA, dA)
    val ab = dot3(dA, dB)
    val bb = dot3(dB, dB)
    val det = aa * bb - ab * ab

    // Near-collinear inks (e.g. two greys) make the fit ambiguous: drive the first ink alone.
    if (det < 1e-4f) {
        val scale = if (aa > 1e-4f) 1f / aa else 0f
        return FloatArray(3) { dA[it] * scale } to FloatArray(3)
    }

    return FloatArray(3) { (bb * dA[it] - ab * dB[it]) / det } to
        FloatArray(3) { (aa * dB[it] - ab * dA[it]) / det }
}

// language=AGSL
internal val RISO_PRINT_AGSL = """
const float PI = 3.14159265359;

uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform shader u_image;

uniform float3 u_paper;
uniform float u_minTransmittance;
uniform float3 u_inkA;
uniform float3 u_inkB;

// Rows of the density pseudo-inverse: coverage = dot(row, density). See separationRows().
uniform float3 u_sepA;
uniform float3 u_sepB;

uniform float2 u_offsetA;
uniform float2 u_offsetB;
uniform float u_angleA;
uniform float u_angleB;

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
    // no ink at all — a two-drum press cannot print white.
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

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;

    float2 sampleA = inkSample(uv, u_offsetA, u_sepA, u_seed);
    float2 sampleB = inkSample(uv, u_offsetB, u_sepB, u_seed + 13.7);

    // Ink gain first, so that dots grow the way ink spreads once it hits the paper...
    float cA = pow(sampleA.x, 1.0 / (1.0 + u_spread));
    float cB = pow(sampleB.x, 1.0 / (1.0 + u_spread));

    cA = screenDots(cA, fragCoord, u_angleA);
    cB = screenDots(cB, fragCoord, u_angleB);

    // ...and blotch last, so an unscreened solid mottles instead of the grain punching holes
    // through the dots.
    cA = inkTexture(cA, fragCoord, u_seed);
    cB = inkTexture(cB, fragCoord, u_seed + 41.3);

    // Stacked ink: the passes multiply, so overlaps go dark.
    float3 stacked = u_paper * mix(float3(1.0), u_inkA, cA) * mix(float3(1.0), u_inkB, cB);

    // Juxtaposed ink: dots land side by side rather than on top of each other, so the overlap
    // averages the two inks instead of subtracting twice.
    float total = cA + cB;
    float3 juxtaposed = total > 0.001
        ? mix(u_paper, (cA * u_inkA + cB * u_inkB) / total, min(total, 1.0))
        : u_paper;

    float3 color = mix(stacked, juxtaposed, u_overprint);
    float opacity = max(sampleA.y, sampleB.y);
    return half4(half3(color * opacity), half(opacity));
}
""".trimIndent()
