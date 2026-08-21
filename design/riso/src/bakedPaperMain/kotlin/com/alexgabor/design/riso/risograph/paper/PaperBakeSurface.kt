package com.alexgabor.design.riso.risograph.paper

import org.jetbrains.skia.Surface

/**
 * Somewhere to draw one bake.
 *
 * The bake is the same draw everywhere; where it lands is not. iOS renders it on the GPU through a
 * Metal context of its own, which is what makes the bake cost milliseconds rather than seconds. The
 * desktop JVM has no GPU surface it can reach — skiko keeps its context to itself — and falls back
 * to a raster one, which is slow enough that [risoPaper] bakes off the composition and stands in
 * until it lands.
 *
 * Whatever comes back is read out through [Surface.readPixels] rather than snapshotted, so the
 * texture the print pass samples is plain CPU pixels and belongs to no context in particular.
 */
internal expect fun newBakeSurface(width: Int, height: Int): Surface
