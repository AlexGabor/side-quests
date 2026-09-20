package com.alexgabor.design.riso.risograph.paper

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TileLoadingTest {

    /** A stock nobody ships, so the tile has to come from the disk or a bake. */
    private val key = RisoPaper(fiberSize = 0.9f).fineTileKey(density = 1f)

    @Test
    fun aTileBakedOnceIsReadFromDiskAfterwards() = runBlocking {
        val dir = Files.createTempDirectory("riso-tiles").toFile()
        try {
            val first = assertNotNull(PaperTiles.load(key, dir.path))
            assertEquals(PaperTiles.Source.Bake, first.source)
            assertTrue(dir.resolve("v$SURFACE_VERSION/${key.fileName}").isFile, "the bake was not cached")

            // A later launch: nothing held in memory, the file is still there.
            val second = assertNotNull(PaperTiles.load(key, dir.path))
            assertEquals(PaperTiles.Source.Disk, second.source)
            assertEquals(first.image.width, second.image.width)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun withNowhereToCacheEveryLoadBakes() = runBlocking {
        assertEquals(PaperTiles.Source.Bake, PaperTiles.load(key, cacheDir = null)?.source)
    }

    @Test
    fun theDefaultStockShips() = runBlocking {
        PaperTiles.shippedKeys.forEach { shipped ->
            assertEquals(PaperTiles.Source.Shipped, PaperTiles.load(shipped, cacheDir = null)?.source, "$shipped")
        }
    }
}
