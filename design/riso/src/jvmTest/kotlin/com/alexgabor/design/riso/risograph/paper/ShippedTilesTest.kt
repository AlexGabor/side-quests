package com.alexgabor.design.riso.risograph.paper

import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The tiles shipped in `composeResources` are exactly what the bake makes today.
 *
 * Anything that changes a tile's pixels — the bake shader, its encoding, a period — has to bump
 * [SURFACE_VERSION] and regenerate them, or the default stock would quietly look different from a
 * custom one baked at runtime. This is what catches the regeneration being forgotten.
 *
 * It is also what regenerates them: `./gradlew :design:riso:bakeTiles` runs it with
 * `riso.tiles.write` set, and it writes the tiles instead of comparing them.
 */
class ShippedTilesTest {

    private val resources = File("src/commonMain/composeResources")

    @Test
    fun shippedTilesMatchAFreshBake() = runBlocking {
        val write = System.getProperty("riso.tiles.write") != null
        val folder = resources.resolve(PaperTiles.SHIPPED_TILES)
        if (write) {
            // Older versions' tiles are never read again.
            folder.parentFile.listFiles()?.filter { it != folder }?.forEach { it.deleteRecursively() }
            folder.mkdirs()
        }

        val stale = PaperTiles.shippedKeys.filter { key ->
            val baked = PaperTiles.bake(key)
            val file = folder.resolve(key.fileName)
            if (write) {
                file.writeBytes(baked.encodePng())
                println("${file.path}  ${file.length() / 1024}KB")
                false
            } else {
                if (!file.isFile) fail("${file.path} is missing: run ./gradlew :design:riso:bakeTiles")
                !pixelsOf(Image.makeFromEncoded(file.readBytes())).contentEquals(pixelsOf(baked.asSkiaBitmap()))
            }
        }
        assertTrue(stale.isEmpty(), "shipped tiles differ from a fresh bake: $stale — bump SURFACE_VERSION and run ./gradlew :design:riso:bakeTiles")
    }

    private fun pixelsOf(image: Image): ByteArray {
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(image.width, image.height)) }
        image.readPixels(bitmap)
        return bitmap.readPixels()!!
    }

    private fun pixelsOf(bitmap: Bitmap): ByteArray = pixelsOf(Image.makeFromBitmap(bitmap))
}
