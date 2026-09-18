package com.alexgabor.lib.share

import org.koin.core.module.Module
import org.koin.dsl.module

actual val shareModule: Module = module {
    single<Share> { NoShare }
}
