package com.alexgabor.pacer.di

import com.alexgabor.pacer.core.settings.settingsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    includes(settingsModule)
}


fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(sharedModule)
    }
}
