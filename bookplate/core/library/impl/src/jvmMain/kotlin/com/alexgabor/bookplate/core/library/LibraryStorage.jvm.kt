package com.alexgabor.bookplate.core.library

import androidx.room3.Room
import okio.FileSystem
import org.koin.core.module.Module
import org.koin.dsl.module

// The JVM only runs tests, so the library lives in memory and covers in a temporary folder.
internal actual val libraryStorageModule: Module = module {
    single { inMemoryLibraryDatabase() }
    single { CoverStore(FileSystem.SYSTEM, FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bookplate-$COVERS_DIRECTORY_NAME") }
}

internal fun inMemoryLibraryDatabase(): LibraryDatabase =
    Room.inMemoryDatabaseBuilder(factory = LibraryDatabaseConstructor::initialize).buildLibraryDatabase()
