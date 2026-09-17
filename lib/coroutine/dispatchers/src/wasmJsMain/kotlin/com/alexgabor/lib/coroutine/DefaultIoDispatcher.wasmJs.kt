package com.alexgabor.lib.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// The browser has no IO dispatcher.
internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.Default
