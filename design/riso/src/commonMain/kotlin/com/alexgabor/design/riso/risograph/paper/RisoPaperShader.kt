package com.alexgabor.design.riso.risograph.paper

import com.alexgabor.design.riso.risograph.ShaderUniforms
import com.alexgabor.design.riso.risograph.color
import com.alexgabor.design.riso.risograph.region.bypassSksl
import kotlin.random.Random

/**
 * The sheet, as shader source: the surface baked once per stock and layout, and the per-frame pass
 * that prints onto it.
 *
 * Ported to AGSL from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture). The same text compiles under Android's
 * `RuntimeShader` and skiko's `RuntimeEffect`; each platform supplies only the binding and, for the
 * bake, somewhere to render into.
 */

/** Whether the stock's surface pushes the artwork around at all. */
internal val RisoPaper.warps: Boolean get() = roughness > 0f || fiber > 0f

/** The sheet shader, built for [capacity] bypassed regions. */
internal fun risoPaperSksl(capacity: Int): String = bypassSksl(capacity) + "\n" + SHEET_SKSL

/**
 * Sets everything the per-frame sheet pass needs beyond its bypass regions and its baked surface.
 *
 * [warps] is whether the surface actually in use pushes the artwork around, which is not always the
 * same question as whether the *stock* does: a caller standing in with a surface too coarse to
 * displace by says no until the real one is ready.
 */
internal fun ShaderUniforms.setSheet(
    paper: RisoPaper,
    width: Float,
    height: Float,
    warps: Boolean,
) {
    float2("u_resolution", width, height)
    float2("u_imageSize", width, height)
    color("u_colorFront", paper.colorFront)
    color("u_colorBack", paper.colorBack)
    float("u_paperWarp", if (warps) 1f else 0f)
}

/**
 * Sets everything the bake needs. The stock's colors are not among them: they are composited by the
 * print shader per frame, so that recoloring the paper does not re-bake its surface.
 */
internal fun ShaderUniforms.setPaperBake(
    paper: RisoPaper,
    width: Float,
    height: Float,
    density: Float,
) {
    float2("u_resolution", width, height)
    float("u_pixelRatio", density)
    float("u_imageAspectRatio", width / height)
    float("u_noiseSize", NOISE_SIZE.toFloat())

    float("u_contrast", paper.contrast.coerceIn(0f, 1f))
    float("u_roughness", paper.roughness)
    float("u_fiber", paper.fiber)
    float("u_fiberSize", paper.fiberSize.coerceAtLeast(0.01f))
    float("u_fade", paper.fade)
    float("u_seed", paper.seed)
    float("u_scale", paper.scale.coerceIn(0.01f, 4f))
}

internal const val NOISE_SIZE = 128

/**
 * A fixed square of random RGB, as packed opaque `0xAARRGGBB`. This replaces paper.design's
 * precomputed noise texture that the surface samples from; each platform wraps it in a repeat-tiled,
 * linearly filtered shader of its own kind.
 */
internal fun noisePixels(size: Int = NOISE_SIZE): IntArray {
    val random = Random(0)
    return IntArray(size * size) {
        val r = random.nextInt(256)
        val g = random.nextInt(256)
        val b = random.nextInt(256)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * The procedural surface: constants, uniforms and the noise the bake is built from. Nothing here
 * reaches the per-frame print shader, which only ever samples the baked result.
 */
// language=AGSL
private val PAPER_SURFACE_SKSL = """
uniform float2 u_resolution;
uniform float u_pixelRatio;

uniform shader u_noiseTexture;
uniform float u_noiseSize;
uniform float u_imageAspectRatio;

uniform float u_contrast;
uniform float u_roughness;
uniform float u_fiber;
uniform float u_fiberSize;
uniform float u_seed;
uniform float u_fade;
uniform float u_scale;

float2 rotate(float2 uv, float th) {
    return float2x2(cos(th), sin(th), -sin(th), cos(th)) * uv;
}

half4 sampleNoise(float2 uv) {
    return u_noiseTexture.eval(fract(uv) * u_noiseSize);
}

float randomR(float2 p) {
    float2 uv = floor(p) / 100.0 + 0.5;
    return float(sampleNoise(uv).r);
}
float randomG(float2 p) {
    float2 uv = floor(p) / 50.0 + 0.5;
    return float(sampleNoise(uv).g);
}
float fiberRandom(float2 p) {
    float2 uv = floor(p) / 100.0;
    return float(sampleNoise(uv).b);
}

float valueNoise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);
    float a = randomR(i);
    float b = randomR(i + float2(1.0, 0.0));
    float c = randomR(i + float2(0.0, 1.0));
    float d = randomR(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(float2 n) {
    float total = 0.0;
    float amplitude = 0.4;
    for (int i = 0; i < 3; i++) {
        total += valueNoise(n) * amplitude;
        n *= 1.99;
        amplitude *= 0.65;
    }
    return total;
}

float roughnessMap(float2 p) {
    p *= 0.1;
    float o = 0.0;
    for (int i = 0; i < 3; i++) {
        float4 w = float4(floor(p), ceil(p));
        float2 f = fract(p);
        o += mix(
            mix(randomG(w.xy), randomG(w.xw), f.y),
            mix(randomG(w.zy), randomG(w.zw), f.y),
            f.x);
        o += 0.2 / exp(2.0 * abs(sin(0.2 * p.x + 0.5 * p.y)));
        p *= 2.1;
    }
    return o / 3.0;
}

float fiberValueNoise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);
    float a = fiberRandom(i);
    float b = fiberRandom(i + float2(1.0, 0.0));
    float c = fiberRandom(i + float2(0.0, 1.0));
    float d = fiberRandom(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fiberNoiseFbm(float2 n, float2 seedOffset) {
    float total = 0.0;
    float amplitude = 1.0;
    for (int i = 0; i < 4; i++) {
        n = rotate(n, 0.7);
        total += fiberValueNoise(n + seedOffset) * amplitude;
        n *= 2.0;
        amplitude *= 0.6;
    }
    return total;
}
float fiberNoise(float2 uv, float2 seedOffset) {
    float epsilon = 0.001;
    float n1 = fiberNoiseFbm(uv + float2(epsilon, 0.0), seedOffset);
    float n2 = fiberNoiseFbm(uv - float2(epsilon, 0.0), seedOffset);
    float n3 = fiberNoiseFbm(uv + float2(0.0, epsilon), seedOffset);
    float n4 = fiberNoiseFbm(uv - float2(0.0, epsilon), seedOffset);
    return length(float2(n1 - n2, n3 - n4)) / (2.0 * epsilon);
}
""".trimIndent()

/**
 * Bake pass: runs the full procedural surface once and encodes the two values the print pass needs
 * into an 8-bit RGBA texture:
 *  - `rg` = the content-UV distortion vector `normalImage.xy`, mapped via `* 0.25 + 0.5`,
 *  - `b`  = the lighting term `res`, mapped via `* 0.5 + 0.5` (already includes `u_contrast`).
 * A small dither (±1/255) is added before quantization to avoid banding on the smooth gradients,
 * and alpha is kept at 1.0 so the data survives premultiplication.
 */
// language=AGSL
internal val PAPER_BAKE_SKSL: String = PAPER_SURFACE_SKSL + """

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;

    float2 patternUV = uv - 0.5;
    patternUV = (5.0 / u_scale) * (patternUV * float2(u_imageAspectRatio, 1.0));

    float2 roughnessUv = 1.5 * (fragCoord - 0.5 * u_resolution) / u_pixelRatio;
    float rough = roughnessMap(roughnessUv + float2(1.0, 0.0)) -
                  roughnessMap(roughnessUv - float2(1.0, 0.0));

    float2 fiberUV = 2.0 / u_fiberSize * patternUV;
    float fiber = fiberNoise(fiberUV, float2(0.0));
    fiber = 0.5 * u_fiber * (fiber - 1.0);

    float fade = u_fade * fbm(0.17 * patternUV + 10.0 * u_seed);
    fade = clamp(8.0 * fade * fade * fade, 0.0, 1.0);

    fiber *= mix(1.0, 0.5, fade);
    rough *= mix(1.0, 0.5, fade);

    float2 normal = float2(0.0);
    float2 normalImage = float2(0.0);

    normal += float2(u_roughness * 1.5 * rough);
    normal += float2(fiber);

    normalImage += float2(u_roughness * 0.75 * rough);
    normalImage += float2(0.2 * fiber);

    float3 lightPos = float3(1.0, 2.0, 1.0);
    float res = dot(normalize(float3(normal, 9.5 - 9.0 * pow(u_contrast, 0.1))),
                    normalize(lightPos));

    float dither = (float(sampleNoise(fragCoord * 0.37).r) - 0.5) / 255.0;
    float2 encNormal = normalImage * 0.25 + 0.5 + dither;
    float encRes = res * 0.5 + 0.5 + dither;
    return half4(half(encNormal.x), half(encNormal.y), half(encRes), half(1.0));
}
""".trimIndent()

// language=AGSL
private val SHEET_SKSL = """
uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform shader u_image;

// The sheet, baked once per stock and layout: rg is how far its surface pushes the artwork around
// (encoded * 0.25 + 0.5), b is how the light falls on it (encoded * 0.5 + 0.5).
uniform shader u_paperMap;
uniform float4 u_colorFront;
uniform float4 u_colorBack;
// A stock with no surface to speak of neither pushes the artwork around nor needs the edge of the
// displaced content antialiased — and the map's 8-bit encoding cannot represent "no push" exactly,
// so it is switched off here rather than left to round to zero.
uniform float u_paperWarp;

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

/** Source-over, for a bypassed window: content untouched, with the sheet showing through it. */
half4 overSheet(half4 content, float frame, float3 sheet, float sheetOpacity) {
    float a = float(content.a) * frame;
    float3 c = float3(content.rgb) * frame;
    return half4(half3(c + sheet * (1.0 - a)), half(a + sheetOpacity * (1.0 - a)));
}

half4 main(float2 fragCoord) {
    // The sheet, baked once per stock and layout: how far its surface pushes the artwork around, and
    // how the light falls on it.
    half4 baked = u_paperMap.eval(fragCoord);
    float2 surface = (float2(baked.r, baked.g) - 0.5) / 0.25;
    float res = clamp(float(baked.b) * 2.0 - 1.0, 0.0, 1.0);

    // The stock itself: its lit front over whatever shows through it. The lighting works on the
    // front's opacity, so a default sheet still takes most of its color from the back.
    float3 sheet = u_colorFront.rgb * u_colorFront.a * res;
    float sheetOpacity = u_colorFront.a * res;
    sheet += u_colorBack.rgb * u_colorBack.a * (1.0 - sheetOpacity);
    sheetOpacity += u_colorBack.a * (1.0 - sheetOpacity);

    // A bypassed region is a window onto the layer: the sheet stops acting on the content there — it
    // is neither pushed around by the surface nor shaded by it — so its pixels arrive exactly as
    // drawn. The stock is still painted behind it, which is what shows through anything translucent.
    float bypass = bypassMask(fragCoord);

    float2 uv = fragCoord / u_resolution;
    float2 warp = u_paperWarp * 0.02 * surface * (1.0 - bypass);
    // Carried in pixels for the reads that take pixels, rather than round-tripping through
    // uv * u_imageSize, which is not exact and would shift a flat stock by a texel.
    float2 warpPx = warp * u_imageSize;
    float frame = mix(1.0, getUvFrame(uv + warp), u_paperWarp);

    half4 source = u_image.eval(clamp(fragCoord + warpPx, float2(0.0), u_imageSize));
    if (bypass >= 1.0) return overSheet(source, frame, sheet, sheetOpacity);

    // What the passes left: the transmittance of every drum that printed here, multiplied together.
    // A pixel no drum reached is transparent and divides out to 1, which leaves the stock alone —
    // and so does a pixel a pass covered but laid no ink on, since that comes through white. Which
    // is why there is no seam where the artwork stops.
    float alpha = float(source.a);
    float3 transmittance = alpha > 0.001
        ? clamp(float3(source.rgb) / alpha, 0.0, 1.0)
        : float3(1.0);

    float cover = alpha * frame;
    // The stock seen through the ink, plus the ink itself wherever the stock is not painted, so an
    // unpainted sheet hands the print back exactly as it was.
    float3 color = sheet * mix(float3(1.0), transmittance, cover);
    color += transmittance * cover * (1.0 - sheetOpacity);
    half4 printed = half4(
        half3(color),
        half(sheetOpacity + cover * (1.0 - sheetOpacity)));

    if (bypass <= 0.0) return printed;
    // Only a bypassed region's antialiased edge reaches here; both sides are premultiplied.
    return mix(printed, overSheet(source, frame, sheet, sheetOpacity), half(bypass));
}
""".trimIndent()
