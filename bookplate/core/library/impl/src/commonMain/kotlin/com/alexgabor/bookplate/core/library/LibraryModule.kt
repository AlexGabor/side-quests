package com.alexgabor.bookplate.core.library

import kotlin.time.Clock
import org.koin.dsl.module

/** The library, its availability checks and relinking: needs a `DocumentAccess` bound alongside it. */
val libraryModule = module {
    includes(libraryStorageModule)
    single { get<LibraryDatabase>().dao() }
    single<LibraryRepository> { LibraryRepositoryImpl(get(), get()) }
    single<BookImporter> { BookImporterImpl(get(), get(), get(), Clock.System) }
    single<BookAvailability> { BookAvailabilityImpl(get(), get()) }
    single<BookRelinker> { BookRelinkerImpl(get(), get(), get()) }
}
