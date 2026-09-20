package com.alexgabor.design.riso.risograph.paper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every platform can bake a tile of its own: the GPU on iOS through its private Metal context, a
 * raster on the JVM. A key nobody ships, so nothing is read in its place.
 */
class TileBakeTest {

    @Test
    fun bakesATileOfTheKeysSize() = runTest {
        val fine = FineTileKey(roughCells = 38, fiberCells = 97, bucket = 1)
        assertEquals(fine.pixels, PaperTiles.bake(fine).width)
        assertEquals(fine.pixels, PaperTiles.bake(fine).height)

        val coarse = CoarseTileKey(fadeCells = 9, seedOffset = 3f)
        assertEquals(coarse.pixels, PaperTiles.bake(coarse).width)
    }
}
