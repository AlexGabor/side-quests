package com.alexgabor.lib.share

import org.koin.dsl.module


val fakeShareModule = module {
    single<Share> { FakeShare() }
}
