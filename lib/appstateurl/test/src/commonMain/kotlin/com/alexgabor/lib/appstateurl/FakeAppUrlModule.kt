package com.alexgabor.lib.appstateurl

import org.koin.dsl.module


val fakeAppUrlModule = module {
    single<AppUrl> { FakeAppUrl() }
}
