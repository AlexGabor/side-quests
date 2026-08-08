package com.alexgabor.design.riso.print

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.compose.ui.graphics.Color
import kotlin.collections.getOrPut
import kotlin.random.Random

/**
 * The paper surface, baked. Everything in this file runs once per stock and layout rather than per
 * frame: the folds, fiber and roughness of a sheet do not depend on what is printed on it, so the
 * expensive procedural pass is rendered into a texture that [risoPrint]'s shader then samples.
 *
 * Ported to AGSL from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture).
 */

/** Whether the stock's surface pushes the artwork around at all. */
internal val RisoPaper.warps: Boolean get() = roughness > 0f || fiber > 0f

/**
 * The baked surface for [paper] at this size, as a shader ready to bind to `u_paperMap`.
 *
 * A stock with neither roughness nor fiber has the same value at every pixel, so it is baked 1x1 and
 * stretched: every size and every call site then shares the one entry, and [RisoPaper.None] costs no
 * surface at all.
 */
internal fun paperMapShader(
    paper: RisoPaper,
    width: Int,
    height: Int,
    density: Float,
): BitmapShader {
    val flat = !paper.warps
    val w = if (flat) 1 else width
    val h = if (flat) 1 else height
    val bitmap = PaperMapCache.get(PaperMapKey(paper, w, h, density)) {
        bakePaperMap(paper, w, h, density)
    }
    return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        .apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
}

/**
 * Renders [PAPER_BAKE_AGSL] once into a [Bitmap], encoding the content-UV distortion vector and the
 * lighting term (see the shader). `RuntimeShader` only executes under hardware rendering, so this
 * draws through a [HardwareRenderer] into an [ImageReader] surface and reads the result back.
 */
private fun bakePaperMap(paper: RisoPaper, width: Int, height: Int, density: Float): Bitmap {
    val shader = RuntimeShader(PAPER_BAKE_AGSL)
    shader.applyPaperParams(paper, width.toFloat(), height.toFloat(), density, createNoiseShader())

    val imageReader = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    val renderer = HardwareRenderer()
    renderer.setSurface(imageReader.surface)
    val node = RenderNode("risoPaperBake")
    node.setPosition(0, 0, width, height)
    val canvas = node.beginRecording()
    canvas.drawRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        Paint().apply { this.shader = shader },
    )
    node.endRecording()
    renderer.setContentRoot(node)
    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

    val image = imageReader.acquireNextImage()
    val hwBitmap = Bitmap.wrapHardwareBuffer(image!!.hardwareBuffer!!, null)!!
    val bitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)

    image.close()
    renderer.destroy()
    imageReader.close()
    return bitmap
}

/**
 * Sets every uniform the bake needs. The stock's colours are not among them: they are composited by
 * the print shader per frame, so that recolouring the paper does not re-bake its surface.
 */
private fun RuntimeShader.applyPaperParams(
    paper: RisoPaper,
    width: Float,
    height: Float,
    density: Float,
    noiseShader: Shader,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_pixelRatio", density)
    setFloatUniform("u_imageAspectRatio", width / height)
    setFloatUniform("u_noiseSize", NOISE_SIZE.toFloat())

    setFloatUniform("u_contrast", paper.contrast.coerceIn(0f, 1f))
    setFloatUniform("u_roughness", paper.roughness)
    setFloatUniform("u_fiber", paper.fiber)
    setFloatUniform("u_fiberSize", paper.fiberSize.coerceAtLeast(0.01f))
    setFloatUniform("u_fade", paper.fade)
    setFloatUniform("u_seed", paper.seed)
    setFloatUniform("u_scale", paper.scale.coerceIn(0.01f, 4f))

    setInputShader("u_noiseTexture", noiseShader)
}

internal fun RuntimeShader.setColorComponents(name: String, color: Color) {
    setFloatUniform(name, color.red, color.green, color.blue, color.alpha)
}

private data class PaperMapKey(
    val paper: RisoPaper,
    val width: Int,
    val height: Int,
    val density: Float,
)

/**
 * Process-wide cache of baked paper maps so that many composables sharing the same stock and size
 * (e.g. list items) reuse a single texture instead of each re-running the heavy bake.
 */
private object PaperMapCache {
    private const val MAX_ENTRIES = 8
    private val entries = object : LinkedHashMap<PaperMapKey, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaperMapKey, Bitmap>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: PaperMapKey, produce: () -> Bitmap): Bitmap =
        entries.getOrPut(key, produce)
}

private const val NOISE_SIZE = 128

/**
 * Generates a fixed random RGB noise bitmap wrapped in a repeat-tiled, linearly filtered shader.
 * This replaces paper.design's precomputed noise texture that the shader samples from.
 */
private fun createNoiseShader(size: Int = NOISE_SIZE): BitmapShader {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(0)
    val pixels = IntArray(size * size) {
        val r = rnd.nextInt(256)
        val g = rnd.nextInt(256)
        val b = rnd.nextInt(256)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
    }
}

/**
 * The procedural surface: constants, uniforms and the noise the bake is built from. Nothing here
 * reaches the per-frame print shader, which only ever samples the baked result.
 */
// language=AGSL
private val PAPER_SURFACE_AGSL = """
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
private val PAPER_BAKE_AGSL: String = PAPER_SURFACE_AGSL + """

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
