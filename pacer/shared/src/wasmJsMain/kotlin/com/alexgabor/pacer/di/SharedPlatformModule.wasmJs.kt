package com.alexgabor.pacer.di

import com.alexgabor.pacer.settings.settingsStoreModule
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val sharedPlatformModule: Module = module {
    includes(settingsStoreModule)
}
