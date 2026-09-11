package com.alexgabor.pacer.core.settings

import org.koin.dsl.module


val fakeSettingsModule = module {
    single<SettingsRepository> { FakeSettingsRepository() }
}
