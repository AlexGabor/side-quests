package com.alexgabor.design.riso.risograph.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// The browser has no file system to keep tiles in. The default stock's tiles ship with the library,
// and anything else is baked once per page load.

internal actual fun readTileFile(dir: String, name: String): ByteArray? = null

internal actual fun writeTileFile(dir: String, name: String, bytes: ByteArray) = Unit

@Composable
@ReadOnlyComposable
internal actual fun tileCacheDir(): String? = null
