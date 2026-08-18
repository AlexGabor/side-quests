package com.alexgabor.design.riso.risograph.paper

import org.jetbrains.skia.Surface

/**
 * A raster surface, because there is no GPU one to be had here.
 *
 * skiko will only hand out a render target for a [org.jetbrains.skia.DirectContext], and reaching
 * the one Compose already draws through means `GLInterface`, `createWebGLContext` and
 * `makeContextCurrent` — all of them internal to skiko. `DirectContext.makeGL()` wraps whatever
 * context is *already* current, which is not something this can arrange from here.
 *
 * So the bake runs on the CPU and takes the better part of a second for a sheet the size of a
 * window. That is why it runs off the composition rather than inline, and why the sheet keeps a
 * stand-in until it lands — see [risoPaper].
 */
internal actual fun newBakeSurface(width: Int, height: Int): Surface =
    Surface.makeRasterN32Premul(width, height)
