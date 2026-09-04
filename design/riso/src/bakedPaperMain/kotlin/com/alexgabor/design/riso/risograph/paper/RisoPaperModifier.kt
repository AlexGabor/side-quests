package com.alexgabor.design.riso.risograph.paper

import androidx.compose.runtime.LaunchedEffect
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
import com.alexgabor.design.riso.risograph.region.setBypass
import com.alexgabor.design.riso.risograph.region.risoBypassHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * How long a layout has to hold still before its surface is baked. Long enough that a resize drag
 * asks for one bake rather than one per frame, short enough not to read as a delay when it isn't one.
 */
private const val BakeSettleMillis = 150L

/**
 * Bakes run one at a time. A resize that outruns the settle can leave a stale bake still going, and
 * letting those pile up across every core would starve the very frames this is trying to protect.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val bakeDispatcher = Dispatchers.Default.limitedParallelism(1)

/**
 * Puts a sheet of paper under whatever this composable draws.
 *
 * ### Performance
 * The sheet is static for a given stock and layout, so its surface is **baked once** into a cached
 * texture (see [paperMapShader]) and shared across every usage with the same key. Nothing else here
 * is per-frame beyond one texture read and one content read.
 *
 * The bake itself is where this parts company with Android. There it is a GPU pass and costs nothing
 * worth naming; on skia — the desktop JVM and iOS both — the same shader runs on the CPU, because
 * neither exposes a GPU surface to render it into. A window-sized sheet then takes the better part of
 * a second, enough that baking it inline turned a resize drag into roughly one frame per bake. So it
 * runs off the composition, and the sheet keeps the surface it already had until the new one is
 * ready: a resize stays at frame rate and the stock's grain is briefly stretched instead.
 */
internal actual fun Modifier.risoPaperEffect(paper: RisoPaper): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val host = remember { RisoBypassHost() }

    // Only the *number* of bypassed regions is read here: it fixes the shader's uniform array
    // lengths, so it has to be known at compile time. Where those regions are is read at draw time.
    val capacity = bypassCapacity(host.peakRegionCount)
    val builder = remember(capacity) {
        RuntimeShaderBuilder(RuntimeEffect.makeForShader(risoPaperSksl(capacity)))
    }
    val uniforms = remember(builder) { RuntimeShaderBuilderUniforms(builder) }

    // The stock at its coarsest: one pixel, which is what a sheet with no surface bakes to anyway.
    // It costs nothing, and because it comes off the same shader it carries exactly the lighting the
    // full surface settles to — so standing in with it changes the sheet's grain, never its color.
    val standIn = remember(paper, density) { paperMapShader(paper, 1, 1, density) }

    // The surface itself is another matter: skia runs a runtime effect on the CPU here, where Android
    // runs the same bake on the GPU, and a sheet the size of a window costs seconds rather than
    // nothing. So it is baked off the composition and the sheet keeps whatever surface it already had
    // until the new one lands — deliberately not keyed on [size], which is what makes a resize reuse
    // it. A drag is coalesced into a single bake by settling first, since the delay is cancelled by
    // the next size. A stock with no surface never gets past the first line and keeps its 1x1.
    var baked by remember(paper, density) { mutableStateOf<Shader?>(null) }
    LaunchedEffect(paper, size, density) {
        if (!paper.warps || size.width <= 0 || size.height <= 0) return@LaunchedEffect
        delay(BakeSettleMillis)
        baked = withContext(bakeDispatcher) {
            paperMapShader(paper, size.width, size.height, density)
        }
    }

    val paperMap = baked ?: standIn
    val ready = remember(builder, paper, size, paperMap) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            false
        } else {
            // One pixel holds one displacement, which would shift the whole print by a fixed
            // fraction of a pixel and then snap when the real surface arrived. So while standing in,
            // the sheet does not push the artwork around at all — the same thing a flat stock does,
            // and for the same reason.
            uniforms.setSheet(paper, w.toFloat(), h.toFloat(), warps = paper.warps && baked != null)
            true
        }
    }

    // The render effect is rebuilt in the draw block rather than in composition, because an
    // ImageFilter bakes in its builder's uniforms and the bypass bounds move with every layout pass.
    // Going through a recomposition would land them a frame late, and a bypassed child would visibly
    // trail its own window on a fling.
    //
    // The surface is bound here rather than in composition for the same reason it is baked off it:
    // it arrives late. An ImageFilter bakes in the child it was built with, and this block is a
    // remembered lambda — were the surface only read during composition, the block would capture
    // nothing that changed when the bake landed, never re-run, and the sheet would keep the blank
    // stand-in for as long as the layout held still.
    //
    // Rebuilt there, but only when those bounds actually moved — see [SheetEffect]. The surface is
    // one of this holder's keys, so the bake landing still gets the filter it needs.
    val sheet = remember(builder, paper, size, paperMap) { SheetEffect() }
    val withEffect = if (ready) {
        Modifier.graphicsLayer {
            clip = true
            val regions = host.regions
            renderEffect = sheet.forRegions(regions) {
                builder.child("u_paperMap", paperMap)
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
