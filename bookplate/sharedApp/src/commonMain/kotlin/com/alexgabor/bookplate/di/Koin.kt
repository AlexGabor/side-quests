package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.library.libraryModule
import com.alexgabor.lib.appstateurl.appUrlModule
import com.alexgabor.lib.documentaccess.documentAccessModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    // The back stack asks for an AppUrl; on a phone there is no address, so this binds one that
    // does nothing.
    includes(appUrlModule)
    includes(documentAccessModule)
    includes(libraryModule)
    includes(platformPlayerModule)
}

/** The app's `BookPlayer`: the Media3 engine on Android, the clock-driven fake where no engine exists yet. */
expect val platformPlayerModule: Module

fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(sharedModule)
    }
}
