package com.alexgabor.bookplate.core.library

import org.koin.dsl.bind
import org.koin.dsl.module

/** Binds one [FakeLibraryRepository] and one [FakeBookImporter] as themselves and as the api interfaces. */
val fakeLibraryModule = module {
    single { FakeLibraryRepository() } bind LibraryRepository::class
    single { FakeBookImporter(get()) } bind BookImporter::class
}
