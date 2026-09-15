package com.alexgabor.lib.appstateurl

import org.koin.core.module.Module
import org.koin.dsl.module

actual val appUrlModule: Module = module {
    single<AppUrl> { NoAppUrl }
}
