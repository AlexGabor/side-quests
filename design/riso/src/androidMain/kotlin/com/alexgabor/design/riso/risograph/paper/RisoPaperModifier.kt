package com.alexgabor.design.riso.risograph.paper

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.alexgabor.design.riso.risograph.region.RisoBypassHost
import com.alexgabor.design.riso.risograph.region.applyBypass
import com.alexgabor.design.riso.risograph.region.bypassAgsl
import com.alexgabor.design.riso.risograph.region.bypassCapacity
import com.alexgabor.design.riso.risograph.region.risoBypassHost

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * ### Performance
 * The sheet is static for a given stock and layout, so its surface is **baked once** into a cached
 * texture (see [paperMapShader]) and shared across every usage with the same key. Nothing else here
 * is per-frame beyond one texture read and one content read.
 */
actual fun Modifier.risoPaper(paper: RisoPaper): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }

    // Only the *number* of bypassed regions is read here: it fixes the shader's uniform array
    // lengths, so it has to be known at compile time. Where those regions are is read at draw time.
    val capacity = bypassCapacity(host.peakRegionCount)
    val shader = remember(capacity) { RuntimeShader(risoPaperAgsl(capacity)) }

    val paperMap = remember(paper, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) null else paperMapShader(paper, w, h, density)
    }

    val ready = remember(shader, paper, size, paperMap) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0 || paperMap == null) {
            false
        } else {
            shader.applySheet(paper, w.toFloat(), h.toFloat(), paperMap)
            true
        }
    }

    // The render effect is rebuilt in the draw block rather than in composition, because a
    // RenderEffect bakes in its shader's uniforms and the bypass bounds move with every layout pass.
    // Going through a recomposition would land them a frame late, and a bypassed child would visibly
    // trail its own window on a fling.
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

    onSizeChanged { size = it }
        .then(Modifier.risoBypassHost(host))
        .then(withEffect)
}

private fun RuntimeShader.applySheet(
    paper: RisoPaper,
    width: Float,
    height: Float,
    paperMap: Shader,
) {
    setFloatUniform("u_resolution", width, height)
    setFloatUniform("u_imageSize", width, height)
    setColorComponents("u_colorFront", paper.colorFront)
    setColorComponents("u_colorBack", paper.colorBack)
    setFloatUniform("u_paperWarp", if (paper.warps) 1f else 0f)
    setInputShader("u_paperMap", paperMap)
}

/** The sheet shader, built for [capacity] bypassed regions. */
internal fun risoPaperAgsl(capacity: Int): String = bypassAgsl(capacity) + "\n" + SHEET_AGSL

// language=AGSL
private val SHEET_AGSL = """
uniform float2 u_resolution;
uniform float2 u_imageSize;
uniform shader u_image;

// The sheet, baked once per stock and layout by RisoPaperBake.kt: rg is how far its surface pushes
// the artwork around (encoded * 0.25 + 0.5), b is how the light falls on it (encoded * 0.5 + 0.5).
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
