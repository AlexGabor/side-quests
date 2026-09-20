package com.alexgabor.design.riso.risograph.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.io.File

internal actual fun readTileFile(dir: String, name: String): ByteArray? =
    File(dir, name).takeIf { it.isFile }?.runCatching { readBytes() }?.getOrNull()

internal actual fun writeTileFile(dir: String, name: String, bytes: ByteArray) {
    runCatching {
        val folder = File(dir).apply { mkdirs() }
        // Written aside and moved into place, so a reader never sees half a tile.
        val partial = File(folder, "$name.partial")
        partial.writeBytes(bytes)
        partial.renameTo(File(folder, name))
    }
}

/** The OS's own cache folder, which the OS may clear — a tile lost there is only baked again. */
private val CacheDir: String by lazy {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        "mac" in os -> "$home/Library/Caches"
        "win" in os -> System.getenv("LOCALAPPDATA") ?: "$home/AppData/Local"
        else -> System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
    }
    "$base/sidequests-riso/tiles"
}

@Composable
@ReadOnlyComposable
internal actual fun tileCacheDir(): String? = CacheDir
