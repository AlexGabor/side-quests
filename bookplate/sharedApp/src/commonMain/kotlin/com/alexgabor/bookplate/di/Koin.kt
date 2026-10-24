package com.alexgabor.bookplate.di

import com.alexgabor.lib.appstateurl.appUrlModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    // The back stack asks for an AppUrl; on a phone there is no address, so this binds one that
    // does nothing.
    includes(appUrlModule)
}


fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(sharedModule)
    }
}
