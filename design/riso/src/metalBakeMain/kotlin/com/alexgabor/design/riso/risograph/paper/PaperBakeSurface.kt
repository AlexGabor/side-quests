package com.alexgabor.design.riso.risograph.paper

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import platform.Metal.MTLCreateSystemDefaultDevice

/**
 * A GPU render target on our own Metal context.
 *
 * Deliberately ours rather than Compose's, which is not exposed — and safely so: Metal has no notion
 * of a current context, so a second device and queue cannot disturb the one drawing the frames. The
 * readback the caller does is what lets the two sides never meet.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun newBakeSurface(width: Int, height: Int): Surface {
    val info = ImageInfo.makeN32Premul(width, height)
    // A device that will not give us a render target is not worth failing the sheet over: a raster
    // surface is slow, not wrong, and it is the only way back from here.
    return Surface.makeRenderTarget(BakeContext, false, info)
        ?: Surface.makeRasterN32Premul(width, height)
}

/** The Metal context the bake renders through, made once and held. */
@OptIn(ExperimentalForeignApi::class)
private val BakeContext: DirectContext by lazy {
    val device = requireNotNull(MTLCreateSystemDefaultDevice()) {
        "No Metal device: cannot bake the paper surface"
    }
    val queue = requireNotNull(device.newCommandQueue()) {
        "No Metal command queue: cannot bake the paper surface"
    }
    DirectContext.makeMetal(device.objcPtr(), queue.objcPtr())
}
