package com.alexgabor.pacer.di

import com.alexgabor.lib.appstateurl.appUrlModule
import com.alexgabor.pacer.core.settings.settingsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    includes(settingsModule, appUrlModule)
}


fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(sharedModule)
    }
}
