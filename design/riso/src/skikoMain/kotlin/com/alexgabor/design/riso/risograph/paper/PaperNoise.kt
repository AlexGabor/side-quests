package com.alexgabor.design.riso.risograph.paper

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

/**
 * [noisePixels] as a repeat-tiled, linearly filtered shader — the `u_noiseTexture` the surface reads
 * its randomness from.
 *
 * Shared by both ways of paying for the surface: iOS binds it to the bake, the desktop JVM and the
 * browser bind it to the print pass itself.
 */
internal fun createNoiseShader(size: Int = NOISE_SIZE): Shader {
    // RGBA_8888 rather than N32, so the byte order the channels are written in is the one skia
    // reads back regardless of the host's endianness — the surface samples r, g and b as three
    // independent random fields, and a swizzle would silently shuffle which is which.
    val pixels = noisePixels(size)
    val bytes = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, argb ->
        bytes[index * 4] = (argb shr 16).toByte()    // r
        bytes[index * 4 + 1] = (argb shr 8).toByte() // g
        bytes[index * 4 + 2] = argb.toByte()         // b
        bytes[index * 4 + 3] = (argb shr 24).toByte() // a, always opaque
    }
    val info = ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, size * 4)
        .makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, SamplingMode.LINEAR)
}

/**
 * The one noise texture, built once. It is a fixed 128×128 square that depends on nothing, and the
 * inline path would otherwise rebuild it on every layout change.
 */
internal val PaperNoiseShader: Shader by lazy { createNoiseShader() }
