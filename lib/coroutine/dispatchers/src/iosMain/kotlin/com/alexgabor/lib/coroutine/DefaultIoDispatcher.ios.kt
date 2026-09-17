package com.alexgabor.lib.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.IO
