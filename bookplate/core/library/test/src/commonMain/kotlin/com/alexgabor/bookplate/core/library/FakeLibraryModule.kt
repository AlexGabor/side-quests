package com.alexgabor.bookplate.core.library

import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds one [FakeLibraryRepository], [FakeBookImporter], [FakeBookAvailability] and [FakeBookRelinker] as themselves
 * and as the api interfaces.
 */
val fakeLibraryModule = module {
    single { FakeLibraryRepository() } bind LibraryRepository::class
    single { FakeBookImporter(get()) } bind BookImporter::class
    single { FakeBookAvailability() } bind BookAvailability::class
    single { FakeBookRelinker() } bind BookRelinker::class
}
