package com.alexgabor.pacer.di

import com.alexgabor.pacer.settings.PacerSettingsRepository
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single { PacerSettingsRepository(get()) }
}


fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(
            sharedModule,
            sharedPlatformModule
        )
    }
}
