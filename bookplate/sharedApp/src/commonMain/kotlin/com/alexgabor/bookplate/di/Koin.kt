package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.library.libraryModule
import com.alexgabor.bookplate.core.player.fakePlayerModule
import com.alexgabor.lib.appstateurl.appUrlModule
import com.alexgabor.lib.documentaccess.documentAccessModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    // The back stack asks for an AppUrl; on a phone there is no address, so this binds one that
    // does nothing.
    includes(appUrlModule)
    includes(documentAccessModule)
    includes(libraryModule)
    // No audio yet: a player that ticks a clock, saving progress to the library. M11 and M12 replace it.
    includes(fakePlayerModule)
}


fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(sharedModule)
    }
}
