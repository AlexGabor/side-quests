package com.alexgabor.design.riso.risograph.paper

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
import com.alexgabor.design.riso.risograph.RuntimeShaderBuilderUniforms
import com.alexgabor.design.riso.risograph.region.RisoBypassHost
import com.alexgabor.design.riso.risograph.region.bypassCapacity
import com.alexgabor.design.riso.risograph.region.risoBypassHost
import com.alexgabor.design.riso.risograph.region.setBypass
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * ### Performance
 * The surface is computed **in the print pass**, per frame, rather than baked into a texture first —
 * which is what the shader this was ported from does, and here it is not so much a choice as the
 * only door left open. Baking wants a GPU surface to render into; skiko's own is reached through
 * plumbing it keeps to itself, and the surface it will hand out instead is a CPU raster that takes
 * seconds for a sheet the size of a window. Paying on the GPU every frame is cheaper than paying
 * once on the CPU, and it costs nothing at startup: the stock is fully grained in the first frame it
 * appears, with no stand-in to swap out from under it.
 *
 * The trade is that the sheet's cost now scales with refresh rate rather than with layout changes,
 * so this is the wrong bargain on a platform that *can* bake — see the Android and iOS
 * implementations, which do.
 *
 * It also comes out slightly truer than a bake: nothing round-trips through the paper map's 8-bit
 * encoding, so the surface reaches the print pass at full precision and needs no dither.
 */
actual fun Modifier.risoPaper(paper: RisoPaper): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }

    // Only the *number* of bypassed regions is read here: it fixes the shader's uniform array
    // lengths, so it has to be known at compile time. Where those regions are is read at draw time.
    val capacity = bypassCapacity(host.peakRegionCount)
    val builder = remember(capacity) {
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(risoPaperInlineSksl(capacity)))
    }
    val uniforms = remember(builder) { RuntimeShaderBuilderUniforms(builder) }

    val ready = remember(builder, paper, size, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            false
        } else {
            uniforms.setSheet(paper, w.toFloat(), h.toFloat(), warps = paper.warps)
            // The surface's own uniforms, which a baking platform would have spent on the bake.
            uniforms.setPaperBake(paper, w.toFloat(), h.toFloat(), density)
            builder.child("u_noiseTexture", PaperNoiseShader)
            true
        }
    }

    // The render effect is rebuilt in the draw block rather than in composition, because an
    // ImageFilter bakes in its builder's uniforms and the bypass bounds move with every layout pass.
    // Going through a recomposition would land them a frame late, and a bypassed child would visibly
    // trail its own window on a fling.
    //
    // Rebuilt there, but only when those bounds actually moved — see [SheetEffect]. Everything else
    // the shader is uniformed with is settled above, which is what this is keyed on.
    val sheet = remember(builder, paper, size, density) { SheetEffect() }
    val withEffect = if (ready) {
        Modifier.graphicsLayer {
            clip = true
            val regions = host.regions
            renderEffect = sheet.forRegions(regions) {
                uniforms.setBypass(regions, capacity)
                ImageFilter
                    .makeRuntimeShader(builder, "u_image", null)
                    .asComposeRenderEffect()
            }
        }
    } else {
        Modifier
    }

    onSizeChanged { size = it }
        .then(Modifier.risoBypassHost(host))
        .then(withEffect)
}
