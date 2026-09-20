package com.alexgabor.design.riso.risograph.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import com.alexgabor.design.riso.risograph.ShaderUniforms

/**
 * What each platform supplies for the sheet: somewhere to bake a tile, a way to repeat one across
 * the sheet, and somewhere to keep it between launches. Everything else — which tile, when, and how
 * it is drawn — is common.
 */

/**
 * Renders [sksl] once into a new [pixels]-square tile, with [uniforms] set on it.
 *
 * Suspends rather than blocks where the platform has no thread to spare for it, so that a bake does
 * not stall the frames around it.
 */
internal expect suspend fun bakeTile(
    sksl: String,
    pixels: Int,
    uniforms: (ShaderUniforms) -> Unit,
): ImageBitmap

/** This tile as a repeat-tiled, linearly filtered shader, ready to bind as a child. */
internal expect fun ImageBitmap.tileShader(): Shader

/** This tile, losslessly, as PNG. */
internal expect fun ImageBitmap.encodePng(): ByteArray

/** The file [name] under [dir], or null if there is none. */
internal expect fun readTileFile(dir: String, name: String): ByteArray?

/** Writes [bytes] to [name] under [dir], creating [dir] if needed. Best effort: failures are dropped. */
internal expect fun writeTileFile(dir: String, name: String, bytes: ByteArray)

/**
 * Where baked tiles are kept between launches, or null where there is nowhere to keep them. Read in
 * composition because Android's answer comes from the context.
 */
@Composable
@ReadOnlyComposable
internal expect fun tileCacheDir(): String?

/**
 * The shader the stock is painted with, re-uniformed only when what it paints changes.
 *
 * Held per sheet: a platform shader carries its uniforms with it, so two sheets cannot share one.
 */
internal expect class StockBrush() {
    fun shader(surface: SheetSurface, density: Float): Shader
}
