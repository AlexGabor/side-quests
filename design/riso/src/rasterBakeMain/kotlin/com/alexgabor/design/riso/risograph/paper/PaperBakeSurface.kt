package com.alexgabor.design.riso.risograph.paper

import org.jetbrains.skia.Surface

/**
 * A raster surface, because there is no GPU one to be had here.
 *
 * skiko will only hand out a render target for a [org.jetbrains.skia.DirectContext], and reaching
 * the one Compose already draws through means `GLInterface`, `createWebGLContext` and
 * `makeContextCurrent` — all of them internal to skiko. So the desktop JVM and the browser bake on
 * the CPU. A tile is small and baked once per shape of sheet, and the default stock's tiles ship
 * with the library, so this is rarely paid at all.
 */
internal actual fun newBakeSurface(width: Int, height: Int): Surface =
    Surface.makeRasterN32Premul(width, height)
