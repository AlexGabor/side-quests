package com.alexgabor.lib.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers to use instead of [Dispatchers] directly, so tests can swap them for test dispatchers.
 */
object CoroutineDispatchers {
    var IO: CoroutineDispatcher = defaultIoDispatcher
    var Default: CoroutineDispatcher = Dispatchers.Default
    var Main: CoroutineDispatcher = Dispatchers.Main

    /** Restores the platform dispatchers. */
    fun reset() {
        IO = defaultIoDispatcher
        Default = Dispatchers.Default
        Main = Dispatchers.Main
    }
}

internal expect val defaultIoDispatcher: CoroutineDispatcher
