package com.alexgabor.design.riso.risograph.paper

import com.alexgabor.design.riso.risograph.ShaderUniforms
import com.alexgabor.design.riso.risograph.color

/**
 * The sheet, as shader source: the bakes that fill its two tiles, the per-frame function that turns
 * them back into a surface, and the stock drawn from it.
 *
 * Ported to AGSL from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture), and reshaped so that its noise repeats: every
 * lattice lookup wraps at the tile's period, octaves step by whole factors, and the fiber's octaves
 * turn by the lattice map `(x, y) -> (x + y, y - x)` — a 45° turn that lands every lattice point on
 * another — rather than by an arbitrary angle that would never come back round.
 *
 * The same text compiles under Android's `RuntimeShader` and skiko's `RuntimeEffect`.
 */

/** Roughness is stored as `rough * ROUGH_ENCODE + 0.5`, room for ±0.625; the default stock reaches ±0.37. */
internal const val ROUGH_ENCODE = 0.8f

/** Fiber is stored as `fiber / FIBER_MAX`; the default stock's gradient peaks near 4.3. */
internal const val FIBER_MAX = 6f

/**
 * The fiber's octaves step by √2 rather than 2 — that is what the lattice map scales by — so there
 * are twice as many of them, each weighted by √0.6 rather than 0.6. That puts more energy in the
 * gradient than the ported shader's four octaves; this brings it back.
 */
private const val FIBER_NORM = 0.574f

/** Lighting of a surface with no relief at all: `normalize(0, 0, z) · normalize(1, 2, 1)`. */
internal const val FLAT_LIGHTING = 0.40824829f

// language=AGSL
private val NOISE_SKSL = """
const float TAU = 6.28318530718;

/** Hash without sine (Dave Hoskins), stable in float for the cell indices a tile reaches. */
float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

/** A random value per lattice cell, repeating every [period] cells along both axes. */
float cellRandom(float2 cell, float period, float salt) {
    return hash12(mod(cell, period) + salt);
}

/** Smoothstepped value noise, periodic in [period] cells. */
float periodicNoise(float2 p, float period, float salt) {
    float2 i = floor(p);
    float2 f = p - i;
    float a = cellRandom(i, period, salt);
    float b = cellRandom(i + float2(1.0, 0.0), period, salt);
    float c = cellRandom(i + float2(0.0, 1.0), period, salt);
    float d = cellRandom(i + float2(1.0, 1.0), period, salt);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
""".trimIndent()

/**
 * Bakes the fine tile: roughness in `r`, fiber in `g`, each in cells of its own lattice. The tile is
 * one period across, so `fragCoord / u_tilePixels` runs over exactly one repeat of both fields.
 */
// language=AGSL
internal val FINE_TILE_FIELDS_SKSL: String = NOISE_SKSL + "\n" + """
const float SALT_ROUGH = 17.0;
const float SALT_FIBER = 131.0;

/** Bilinear lattice noise plus a ridge, over three doubling octaves. */
float roughnessMap(float2 p, float period) {
    // The ridge's direction, rounded to whole turns per period so that it repeats with the tile.
    float fx = max(1.0, floor(0.2 * period / TAU + 0.5));
    float fy = max(1.0, floor(0.5 * period / TAU + 0.5));
    float octavePeriod = period;
    float o = 0.0;
    for (int i = 0; i < 3; i++) {
        float2 c = floor(p);
        float2 f = p - c;
        o += mix(
            mix(cellRandom(c, octavePeriod, SALT_ROUGH), cellRandom(c + float2(0.0, 1.0), octavePeriod, SALT_ROUGH), f.y),
            mix(cellRandom(c + float2(1.0, 0.0), octavePeriod, SALT_ROUGH), cellRandom(c + float2(1.0, 1.0), octavePeriod, SALT_ROUGH), f.y),
            f.x);
        o += 0.2 / exp(2.0 * abs(sin(TAU * (fx * p.x + fy * p.y) / period)));
        p *= 2.0;
        octavePeriod *= 2.0;
    }
    return o / 3.0;
}

/**
 * Seven octaves, each turned by the lattice map and so √2 finer than the last. Octave k is read at
 * M^k·n, which repeats every period·2^floor(k/2) along each axis — the period only has to double on
 * every second octave, since M^2 is a doubling and a quarter turn.
 */
float fiberFbm(float2 n, float period) {
    float total = 0.0;
    float amplitude = 1.0;
    float octavePeriod = period;
    for (int k = 0; k < 7; k++) {
        total += periodicNoise(n + float(k) * 7.31, octavePeriod, SALT_FIBER) * amplitude;
        n = float2(n.x + n.y, n.y - n.x);
        if (k == 1 || k == 3 || k == 5) octavePeriod *= 2.0;
        amplitude *= 0.7745967;
    }
    return total * $FIBER_NORM;
}

float fiberGradient(float2 n, float period) {
    float epsilon = 0.001;
    float n1 = fiberFbm(n + float2(epsilon, 0.0), period);
    float n2 = fiberFbm(n - float2(epsilon, 0.0), period);
    float n3 = fiberFbm(n + float2(0.0, epsilon), period);
    float n4 = fiberFbm(n - float2(0.0, epsilon), period);
    return length(float2(n1 - n2, n3 - n4)) / (2.0 * epsilon);
}

/** Both fields at [t], a position within the tile's one period (0..1 along each axis). */
float2 fineFields(float2 t, float roughCells, float fiberCells) {
    float2 rp = t * roughCells;
    float rough = roughnessMap(rp + float2(0.1, 0.0), roughCells) -
                  roughnessMap(rp - float2(0.1, 0.0), roughCells);
    return float2(rough, fiberGradient(t * fiberCells, fiberCells));
}
""".trimIndent()

// language=AGSL
internal val FINE_TILE_BAKE_SKSL: String = FINE_TILE_FIELDS_SKSL + "\n" + """
uniform float u_tilePixels;
uniform float u_roughCells;
uniform float u_fiberCells;

half4 main(float2 fragCoord) {
    float2 fields = fineFields(fragCoord / u_tilePixels, u_roughCells, u_fiberCells);
    float rough = fields.x;
    float fiber = fields.y;

    // Half a level of dither either way, so the smooth stretches do not band once quantized.
    float dither = (hash12(fragCoord + 91.7) - 0.5) / 255.0;
    return half4(
        half(rough * $ROUGH_ENCODE + 0.5 + dither),
        half(fiber / $FIBER_MAX + dither),
        0.0,
        1.0);
}
""".trimIndent()

/** Bakes the coarse tile: the fade's raw fbm in `r`. */
// language=AGSL
internal val COARSE_TILE_FIELDS_SKSL: String = NOISE_SKSL + "\n" + """
const float SALT_FADE = 283.0;

float fadeFbm(float2 p, float period) {
    float total = 0.0;
    float amplitude = 0.4;
    float octavePeriod = period;
    for (int i = 0; i < 3; i++) {
        total += periodicNoise(p, octavePeriod, SALT_FADE) * amplitude;
        p *= 2.0;
        octavePeriod *= 2.0;
        amplitude *= 0.65;
    }
    return total;
}
""".trimIndent()

// language=AGSL
internal val COARSE_TILE_BAKE_SKSL: String = COARSE_TILE_FIELDS_SKSL + "\n" + """
uniform float u_tilePixels;
uniform float u_fadeCells;
uniform float u_seedOffset;

half4 main(float2 fragCoord) {
    float2 p = fragCoord / u_tilePixels * u_fadeCells + u_seedOffset;
    float dither = (hash12(fragCoord + 53.3) - 0.5) / 255.0;
    return half4(half(fadeFbm(p, u_fadeCells) + dither), 0.0, 0.0, 1.0);
}
""".trimIndent()

/**
 * The surface at a point on the sheet, rebuilt per frame from the two tiles and the stock's
 * strengths. Shared, as source, by everything that needs the surface — the stock and the ink that
 * warps across it — so the two can never disagree about where the sheet dips.
 *
 * `sheetPx` is in pixels from the sheet's own top-left.
 */
// language=AGSL
internal val SHEET_SURFACE_SKSL = """
uniform shader u_fineTile;
uniform shader u_coarseTile;
// Tile pixels per sheet pixel: the tile's density over the screen's.
uniform float u_fineScale;
uniform float u_coarseScale;
// 0 until the tiles have landed: the surface is read as flat until then.
uniform float u_surfaceReady;

uniform float u_contrast;
uniform float u_roughness;
uniform float u_fiber;
uniform float u_fade;

void sheetSurface(float2 sheetPx, out float2 normalImage, out float res) {
    if (u_surfaceReady < 0.5) {
        normalImage = float2(0.0);
        res = $FLAT_LIGHTING;
        return;
    }
    half4 fine = u_fineTile.eval(sheetPx * u_fineScale);
    float rough = (float(fine.r) - 0.5) / $ROUGH_ENCODE;
    float fiber = float(fine.g) * $FIBER_MAX;
    float fadeNoise = float(u_coarseTile.eval(sheetPx * u_coarseScale).r);

    float fade = u_fade * fadeNoise;
    fade = clamp(8.0 * fade * fade * fade, 0.0, 1.0);

    fiber = 0.5 * u_fiber * (fiber - 1.0);
    fiber *= mix(1.0, 0.5, fade);
    rough *= mix(1.0, 0.5, fade);

    float2 normal = float2(u_roughness * 1.5 * rough + fiber);
    normalImage = float2(u_roughness * 0.75 * rough + 0.2 * fiber);

    float3 lightPos = float3(1.0, 2.0, 1.0);
    res = dot(normalize(float3(normal, 9.5 - 9.0 * pow(u_contrast, 0.1))), normalize(lightPos));
    res = clamp(res, 0.0, 1.0);
}
""".trimIndent()

/**
 * The stock, painted behind the sheet's content: its lit front over whatever shows through it,
 * premultiplied. Nothing is read from the content — the ink multiplies onto this afterwards.
 */
// language=AGSL
internal val STOCK_SKSL: String = SHEET_SURFACE_SKSL + "\n" + """
uniform float4 u_colorFront;
uniform float4 u_colorBack;

half4 main(float2 fragCoord) {
    float2 normalImage;
    float res;
    sheetSurface(fragCoord, normalImage, res);

    float3 sheet = u_colorFront.rgb * u_colorFront.a * res;
    float sheetOpacity = u_colorFront.a * res;
    sheet += u_colorBack.rgb * u_colorBack.a * (1.0 - sheetOpacity);
    sheetOpacity += u_colorBack.a * (1.0 - sheetOpacity);
    return half4(half3(sheet), half(sheetOpacity));
}
""".trimIndent()

internal fun ShaderUniforms.setFineTileBake(key: FineTileKey) {
    float("u_tilePixels", key.pixels.toFloat())
    float("u_roughCells", key.roughCells.toFloat())
    float("u_fiberCells", key.fiberCells.toFloat())
}

internal fun ShaderUniforms.setCoarseTileBake(key: CoarseTileKey) {
    float("u_tilePixels", key.pixels.toFloat())
    float("u_fadeCells", key.fadeCells.toFloat())
    float("u_seedOffset", key.seedOffset)
}

/**
 * Sets the uniforms of [SHEET_SURFACE_SKSL] — not its tiles, which each platform binds as children
 * of its own kind.
 *
 * [ready] is whether real tiles are bound. A stock with no relief is always read flat.
 */
internal fun ShaderUniforms.setSheetSurface(paper: RisoPaper, density: Float, ready: Boolean) {
    val bucket = densityBucket(density)
    float("u_fineScale", bucket / density)
    float("u_coarseScale", 1f / (COARSE_DP_PER_PIXEL * density))
    float("u_surfaceReady", if (ready && paper.warps) 1f else 0f)
    float("u_contrast", paper.contrast.coerceIn(0f, 1f))
    float("u_roughness", paper.roughness)
    float("u_fiber", paper.fiber)
    float("u_fade", paper.fade)
}

internal fun ShaderUniforms.setStock(paper: RisoPaper) {
    color("u_colorFront", paper.colorFront)
    color("u_colorBack", paper.colorBack)
}
