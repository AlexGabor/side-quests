package com.alexgabor.design.riso.risograph.inks

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.ln

/**
 * One drum's pass, as an AGSL effect over the recorded artwork.
 *
 * The shader is compiled once and re-uniformed per draw — a `RenderEffect` bakes in its shader's
 * uniforms, so the effect itself is rebuilt each time, but the compile is not.
 */
internal actual class InkPass actual constructor() {

    private val shader = RuntimeShader(INK_PASS_AGSL)

    actual fun effect(spec: InkPassSpec): RenderEffect? {
        val paper = spec.paper.transmittance()
        shader.setFloatUniform("u_paper", paper[0], paper[1], paper[2])
        shader.setFloatUniform("u_minTransmittance", MIN_TRANSMITTANCE)
        // Authored as a fraction darker than the stock; the shader wants the density that
        // corresponds to, since that is what it subtracts.
        shader.setFloatUniform("u_paperFloor", -ln(1f - spec.tolerance.coerceIn(0f, 0.5f)))

        val ink = spec.ink.transmittance()
        shader.setFloatUniform("u_ink", ink[0], ink[1], ink[2])
        shader.setFloatUniform("u_row", spec.row[0], spec.row[1], spec.row[2])

        shader.setFloatUniform("u_origin", spec.origin.x, spec.origin.y)
        shader.setFloatUniform("u_angle", spec.screenAngle)
        shader.setFloatUniform("u_phase", spec.phase)
        shader.setFloatUniform("u_screen", spec.screen.coerceIn(0f, 1f))
        shader.setFloatUniform("u_dotSize", spec.dotSize.coerceAtLeast(1.5f))
        shader.setFloatUniform("u_mottle", spec.mottle.coerceIn(0f, 1f))
        shader.setFloatUniform("u_mottleSize", spec.mottleSize.coerceAtLeast(1f))
        shader.setFloatUniform("u_grain", spec.grain.coerceIn(0f, 1f))
        shader.setFloatUniform("u_grainSize", spec.grainSize.coerceAtLeast(1f))
        shader.setFloatUniform("u_spread", spec.spread.coerceIn(0f, 1f))

        return AndroidRenderEffect
            .createRuntimeShaderEffect(shader, "u_image")
            .asComposeRenderEffect()
    }
}

/**
 * Lower bound on a transmittance. A channel that transmits nothing has infinite optical density, so
 * pure black is treated as a very dark — but finite — ink. Kept in step with
 * [MIN_TRANSMITTANCE][com.alexgabor.design.riso.risograph.inks.MIN_TRANSMITTANCE]; if they disagreed, the
 * darkest colors would separate into more ink than the inks themselves can lay down.
 */
private const val MIN_TRANSMITTANCE = 0.02f

/**
 * One drum, end to end. There is no loop and no array here: this shader runs on a layer that is
 * already one pass, and knows about exactly one ink.
 *
 * It returns opaque color, never transparency, because the pass is composited with
 * [BlendMode.Multiply][androidx.compose.ui.graphics.BlendMode.Multiply]. White is what a drum that
 * laid no ink hands back, and white multiplies to nothing — so bare paper comes through the pass
 * untouched, and there is no seam where the artwork stops.
 */
// language=AGSL
private val INK_PASS_AGSL = """
const float PI = 3.14159265359;

/**
 * Coverage below which a drum is not worth screening. A little over one percent of an ink moves a
 * channel by about two levels out of 255 — less than its own grain would — so the ink is laid flat
 * rather than put through the screen and the mottle. Dropping it instead would pop as a color
 * crossed the threshold.
 */
const float FAINT_COVERAGE = 0.012;

uniform shader u_image;

uniform float3 u_paper;
uniform float u_minTransmittance;
uniform float u_paperFloor;

uniform float3 u_ink;
uniform float3 u_row;

// Where this pass sits on the sheet. The screen and the mottle are properties of the press, not of
// the artwork, so they are read at the pass's place on the page rather than at its own origin —
// otherwise every region would start its dot grid afresh and two identical buttons would carry
// identical blotches.
uniform float2 u_origin;
uniform float u_angle;
uniform float u_phase;
uniform float u_screen;
uniform float u_dotSize;
uniform float u_mottle;
uniform float u_mottleSize;
uniform float u_grain;
uniform float u_grainSize;
uniform float u_spread;

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
 * How much darker than bare paper a color is, per channel. Lighter than paper reads as no ink at
 * all — a press cannot print white — and so does anything within u_paperFloor of the stock, which is
 * what keeps artwork authored to the paper color from separating into a tint no press could hold.
 * The floor is subtracted rather than thresholded, so ink fades in instead of switching on.
 */
float3 densityOfRgb(float3 rgb) {
    return max(-log(clamp(rgb / u_paper, u_minTransmittance, 1.0)) - u_paperFloor, 0.0);
}

/** The blotchiness and speckle of ink actually laid down on paper. */
float inkTexture(float coverage, float2 sheet) {
    coverage *= 1.0 - u_mottle * (1.0 - fbm(sheet / u_mottleSize + u_phase));
    coverage *= 1.0 - u_grain * hash(floor(sheet / u_grainSize) + u_phase);
    return clamp(coverage, 0.0, 1.0);
}

/**
 * Thresholds coverage against a rotated dot screen, so tone becomes dots of varying size.
 *
 * This is also what makes an overprint of two tints read bright: each drum screens at its own angle,
 * so their dots land beside each other on the paper rather than on top of each other, and the eye
 * mixes them additively. Two *solid* passes still multiply to a dark overprint, as they do on a real
 * press — brightness comes from the tint, not from the stacking.
 */
float screenDots(float coverage, float2 sheet) {
    if (u_screen <= 0.0) return coverage;
    float2 p = rotate(sheet, u_angle) * (PI / u_dotSize);
    float field = 0.5 - 0.5 * cos(p.x) * cos(p.y);
    // Soften the threshold, and stretch coverage past the field's 0..1 range so that full coverage
    // fills the cell corners solid instead of leaving holes.
    float w = clamp(2.0 / u_dotSize, 0.06, 0.45);
    float t = coverage * (1.0 + 2.0 * w) - w;
    return mix(coverage, clamp((t - field) / w + 0.5, 0.0, 1.0), u_screen);
}

half4 main(float2 fragCoord) {
    half4 src = u_image.eval(fragCoord);
    float2 sheet = fragCoord + u_origin;

    // Unpremultiplied, so that an antialiased edge is read as its own color at partial coverage
    // rather than as that color fading towards black. Nothing drawn at all reads as bare paper,
    // which separates to no ink and comes back as white below.
    float alpha = float(src.a);
    float3 rgb = alpha > 0.001 ? float3(src.rgb) / alpha : float3(1.0);

    float coverage = clamp(dot(u_row, densityOfRgb(rgb)), 0.0, 1.0) * alpha;

    if (coverage > FAINT_COVERAGE) {
        // Ink gain first, so dots grow the way ink spreads once it hits the paper, then the screen,
        // then blotching last — otherwise the grain punches holes through the dots instead of
        // mottling a solid.
        coverage = pow(coverage, 1.0 / (1.0 + u_spread));
        coverage = screenDots(coverage, sheet);
        coverage = inkTexture(coverage, sheet);
    }

    return half4(half3(mix(float3(1.0), u_ink, coverage)), 1.0);
}
""".trimIndent()
