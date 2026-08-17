package com.alexgabor.design.riso.risograph.paper

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
import com.alexgabor.design.riso.risograph.RuntimeShaderUniforms
import com.alexgabor.design.riso.risograph.region.RisoBypassHost
import com.alexgabor.design.riso.risograph.region.bypassCapacity
import com.alexgabor.design.riso.risograph.region.setBypass
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
    val shader = remember(capacity) { RuntimeShader(risoPaperSksl(capacity)) }
    val uniforms = remember(shader) { RuntimeShaderUniforms(shader) }

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
            uniforms.setSheet(paper, w.toFloat(), h.toFloat(), warps = paper.warps)
            shader.setInputShader("u_paperMap", paperMap)
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
            uniforms.setBypass(host.regions, capacity)
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
