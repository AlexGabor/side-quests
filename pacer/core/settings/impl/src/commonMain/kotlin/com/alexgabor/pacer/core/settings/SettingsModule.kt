package com.alexgabor.pacer.core.settings

import org.koin.dsl.module


val settingsModule = module {
    includes(settingsStoreModule)
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
}
