package com.alexgabor.bookplate.debug

import org.koin.core.module.Module
import org.koin.dsl.module

// TEMPORARY (M7 debug entry): removed in M9.
// The JVM only runs tests, so picks are kept in memory.
internal actual val debugPickStoreModule: Module = module {
    single<DebugPickStore> { InMemoryDebugPickStore() }
}
