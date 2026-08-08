package com.alexgabor.design.riso.paper

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
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
import kotlin.collections.getOrPut
import kotlin.random.Random

/**
 * Applies a static, procedural paper/cardboard texture on top of whatever the composable draws,
 * ported to AGSL from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture).
 *
 * The composable's own rendered output is fed into the shader as its image input, so the paper's
 * fiber and roughness subtly distort and shade the content, like a paper print.
 *
 * ### Performance
 * The expensive procedural surface (fiber, roughness, lighting) is static
 * for a given [params] and layout size, so it is **baked once** into a cached texture
 * (see [bakePaperMap]) and shared across all usages with the same key. Each frame only runs a
 * lightweight composite shader that samples the baked map plus the live content, keeping scroll
 * cheap for both static and dynamic content.
 */
actual fun Modifier.paperTexture(params: PaperTextureParams): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }

    // Only the *number* of bypassed regions is read here: it fixes the shader's uniform array
    // lengths, so it has to be known at compile time. Where those regions are is read at draw time
    // instead, below.
    val capacity = bypassCapacity(host.peakRegionCount)
    val compositeShader = remember(capacity) { RuntimeShader(paperCompositeAgsl(capacity)) }

    val ready = remember(compositeShader, params, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            false
        } else {
            // Keyed on params and size only, so regions moving never re-bake the paper surface.
            val paperMap = PaperMapCache.get(PaperMapKey(params, w, h, density)) {
                bakePaperMap(params, w, h, density)
            }
            val paperShader = BitmapShader(paperMap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
            compositeShader.applyCompositeParams(params, w.toFloat(), h.toFloat(), paperShader)
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
            compositeShader.applyBypass(host.regions, capacity)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(compositeShader, "u_image")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    onSizeChanged { size = it }.then(Modifier.risoBypassHost(host)).then(withEffect)
}

/**
 * Sets every uniform the paper shader needs. The composable's content is bound separately as the
 * `u_image` input by the render effect, so the shader runs in image-filter mode with the content
 * mapped 1:1 to the layer pixels (no aspect-fit letterboxing).
 */
private fun RuntimeShader.applyPaperParams(
    params: PaperTextureParams,
    width: Float,
    height: Float,
    density: Float,
    noiseShader: Shader,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_pixelRatio", density)
    setFloatUniform("u_hasImage", 1f)
    setFloatUniform("u_imageAspectRatio", width / height)
    setFloatUniform("u_imageSize", width, height)
    setFloatUniform("u_noiseSize", NOISE_SIZE.toFloat())

    setColorComponents("u_colorFront", params.colorFront)
    setColorComponents("u_colorBack", params.colorBack)

    setFloatUniform("u_contrast", params.contrast)
    setFloatUniform("u_roughness", params.roughness)
    setFloatUniform("u_fiber", params.fiber)
    setFloatUniform("u_fiberSize", params.fiberSize.coerceAtLeast(0.01f))
    setFloatUniform("u_fade", params.fade)
    setFloatUniform("u_seed", params.seed)
    setFloatUniform("u_scale", params.scale.coerceIn(0.01f, 4f))

    setInputShader("u_noiseTexture", noiseShader)
}

private fun RuntimeShader.setColorComponents(name: String, color: Color) {
    setFloatUniform(name, color.red, color.green, color.blue, color.alpha)
}

/**
 * Sets the uniforms for the lightweight composite shader. The paper surface is supplied
 * pre-baked as [paperShader]; the composable's content is bound separately as `u_image` by the
 * render effect.
 */
private fun RuntimeShader.applyCompositeParams(
    params: PaperTextureParams,
    width: Float,
    height: Float,
    paperShader: Shader,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_imageSize", width, height)
    setFloatUniform("u_contrast", params.contrast)
    setColorComponents("u_colorFront", params.colorFront)
    setColorComponents("u_colorBack", params.colorBack)
    setInputShader("u_paperMap", paperShader)
}

/**
 * Renders the heavy [PAPER_BAKE_AGSL] surface once into an [Bitmap], encoding the content-UV
 * distortion vector and the lighting term (see the shader). `RuntimeShader` only executes under
 * hardware rendering, so this draws through a [HardwareRenderer] into an [ImageReader] surface and
 * reads the result back into a bitmap (same pipeline as `PaperTextureShaderTest`).
 */
private fun bakePaperMap(params: PaperTextureParams, width: Int, height: Int, density: Float): Bitmap {
    val shader = RuntimeShader(PAPER_BAKE_AGSL)
    shader.applyPaperParams(params, width.toFloat(), height.toFloat(), density, createNoiseShader())

    val imageReader = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    val renderer = HardwareRenderer()
    renderer.setSurface(imageReader.surface)
    val node = RenderNode("paperTextureBake")
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

private data class PaperMapKey(
    val params: PaperTextureParams,
    val width: Int,
    val height: Int,
    val density: Float,
)

/**
 * Process-wide cache of baked paper maps so that many composables sharing the same params/size
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

// language=AGSL
internal val PAPER_TEXTURE_AGSL = """
uniform float2 u_resolution;
uniform float u_pixelRatio;

uniform shader u_image;
uniform shader u_noiseTexture;
uniform float u_noiseSize;
uniform float2 u_imageSize;
uniform float u_hasImage;
uniform float u_imageAspectRatio;

uniform float4 u_colorFront;
uniform float4 u_colorBack;

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

float getUvFrame(float2 uv) {
    float aax = 2.0 / u_resolution.x;
    float aay = 2.0 / u_resolution.y;
    float left = smoothstep(0.0, aax, uv.x);
    float right = 1.0 - smoothstep(1.0 - aax, 1.0, uv.x);
    float bottom = smoothstep(0.0, aay, uv.y);
    float top = 1.0 - smoothstep(1.0 - aay, 1.0, uv.y);
    return left * right * bottom * top;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution;
    float canvasAspect = u_resolution.x / u_resolution.y;

    float2 imageUV = uv;
    if (u_hasImage > 0.5) {
        float2 st = uv - 0.5;
        if (canvasAspect > u_imageAspectRatio) {
            st.y *= u_imageAspectRatio / canvasAspect;
        } else {
            st.x *= canvasAspect / u_imageAspectRatio;
        }
        imageUV = st + 0.5;
    }

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

    float3 fgColor = u_colorFront.rgb * u_colorFront.a;
    float fgOpacity = u_colorFront.a;
    float3 bgColor = u_colorBack.rgb * u_colorBack.a;
    float bgOpacity = u_colorBack.a;

    float frame = 0.0;
    half4 image = half4(0.0);
    if (u_hasImage > 0.5) {
        imageUV += 0.02 * normalImage;
        frame = getUvFrame(imageUV);
        image = u_image.eval(clamp(imageUV, 0.0, 1.0) * u_imageSize);
        if (image.a > 0.0) {
            image.rgb /= image.a;
        }
        image.rgb += half3(0.6 * pow(u_contrast, 0.4) * (res - 0.7));
        frame *= float(image.a);
    }

    float3 color = fgColor * res;
    float opacity = fgOpacity * res;
    color += bgColor * (1.0 - opacity);
    opacity += bgOpacity * (1.0 - opacity);
    opacity = mix(opacity, 1.0, frame);
    color = mix(color, float3(image.rgb), frame);

    return half4(half3(color), half(opacity));
}
""".trimIndent()

/**
 * Shared AGSL prelude (constants, uniforms and helper functions) for the paper shaders, derived
 * from [PAPER_TEXTURE_AGSL] so the procedural math stays in one place. The `u_image` input is
 * stripped because the bake pass does not sample any content.
 */
private val PAPER_COMMON_AGSL: String = PAPER_TEXTURE_AGSL
    .substringBefore("half4 main(")
    .replace("uniform shader u_image;\n", "")

/**
 * Bake pass: runs the full procedural surface once and encodes the two values the composite pass
 * needs into an 8-bit RGBA texture:
 *  - `rg` = the content-UV distortion vector `normalImage.xy`, mapped via `* 0.25 + 0.5`,
 *  - `b`  = the lighting term `res`, mapped via `* 0.5 + 0.5` (already includes `u_contrast`).
 * A small dither (±1/255) is added before quantization to avoid banding on the smooth gradients,
 * and alpha is kept at 1.0 so the data survives premultiplication.
 */
// language=AGSL
private val PAPER_BAKE_AGSL: String = PAPER_COMMON_AGSL + """
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

/**
 * The composite shader, built for [capacity] bypassed regions. See [bypassAgsl] for why the
 * capacity is part of the source rather than a uniform.
 */
private fun paperCompositeAgsl(capacity: Int): String =
    bypassAgsl(capacity) + "\n" + PAPER_COMPOSITE_AGSL

/**
 * Composite pass: the lightweight per-frame shader. It reads the pre-baked paper map, reconstructs
 * the distortion vector and lighting term, samples the live content (`u_image`, bound by the
 * render effect), and blends exactly like [PAPER_TEXTURE_AGSL]'s image path.
 */
// language=AGSL
private val PAPER_COMPOSITE_AGSL: String = """
uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform float u_contrast;
uniform float4 u_colorFront;
uniform float4 u_colorBack;

uniform shader u_image;
uniform shader u_paperMap;

float getUvFrame(float2 uv) {
    float aax = 2.0 / u_resolution.x;
    float aay = 2.0 / u_resolution.y;
    float left = smoothstep(0.0, aax, uv.x);
    float right = 1.0 - smoothstep(1.0 - aax, 1.0, uv.x);
    float bottom = smoothstep(0.0, aay, uv.y);
    float top = 1.0 - smoothstep(1.0 - aay, 1.0, uv.y);
    return left * right * bottom * top;
}

half4 main(float2 fragCoord) {
    half4 paper = u_paperMap.eval(fragCoord);
    float2 normalImage = (float2(paper.r, paper.g) - 0.5) / 0.25;
    float res = float(paper.b) * 2.0 - 1.0;

    // Inside a bypassed region the sheet stops acting on the content: it is neither pushed around
    // by the grain nor shaded by the paper's lighting, so its pixels arrive exactly as drawn. The
    // paper behind it is composited as usual, which is what shows through anything translucent.
    float bypass = bypassMask(fragCoord);

    float2 imageUV = fragCoord / u_resolution;
    imageUV += 0.02 * normalImage * (1.0 - bypass);
    float frame = getUvFrame(imageUV);
    half4 image = u_image.eval(clamp(imageUV, 0.0, 1.0) * u_imageSize);
    if (image.a > 0.0) {
        image.rgb /= image.a;
    }
    image.rgb += half3((1.0 - bypass) * 0.6 * pow(u_contrast, 0.4) * (res - 0.7));
    frame *= float(image.a);

    float3 fgColor = u_colorFront.rgb * u_colorFront.a;
    float fgOpacity = u_colorFront.a;
    float3 bgColor = u_colorBack.rgb * u_colorBack.a;
    float bgOpacity = u_colorBack.a;

    float3 color = fgColor * res;
    float opacity = fgOpacity * res;
    color += bgColor * (1.0 - opacity);
    opacity += bgOpacity * (1.0 - opacity);
    opacity = mix(opacity, 1.0, frame);
    color = mix(color, float3(image.rgb), frame);

    return half4(half3(color), half(opacity));
}
""".trimIndent()
