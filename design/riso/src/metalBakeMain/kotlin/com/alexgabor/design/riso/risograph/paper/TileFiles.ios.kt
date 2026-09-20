package com.alexgabor.design.riso.risograph.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun readTileFile(dir: String, name: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile("$dir/$name") ?: return null
    val size = data.length.toInt()
    if (size == 0) return null
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeTileFile(dir: String, name: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    // Atomically, so a reader never sees half a tile.
    data.writeToFile("$dir/$name", atomically = true)
}

/** The app's Caches folder, which iOS may clear — a tile lost there is only baked again. */
private val CacheDir: String? by lazy {
    (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?.let { "$it/riso-tiles" }
}

@Composable
@ReadOnlyComposable
internal actual fun tileCacheDir(): String? = CacheDir
