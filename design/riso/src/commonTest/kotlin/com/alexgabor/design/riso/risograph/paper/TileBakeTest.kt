package com.alexgabor.design.riso.risograph.paper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every platform can bake a tile of its own: the GPU on iOS through its private Metal context, a
 * raster on the JVM and in the browser. A key nobody ships, so nothing is read in its place.
 *
 * Each key is baked once and then measured. A bake is expensive — seconds, in the browser, where
 * the shader is evaluated per pixel on the CPU — so it is not worth paying twice to read a width
 * and a height.
 */
class TileBakeTest {

    @Test
    fun bakesATileOfTheKeysSize() = runTest {
        val fine = FineTileKey(roughCells = 38, fiberCells = 97, bucket = 1)
        val fineTile = PaperTiles.bake(fine)
        assertEquals(fine.pixels, fineTile.width)
        assertEquals(fine.pixels, fineTile.height)

        val coarse = CoarseTileKey(fadeCells = 9, seedOffset = 3f)
        assertEquals(coarse.pixels, PaperTiles.bake(coarse).width)
    }
}
