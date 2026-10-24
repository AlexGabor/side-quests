package com.alexgabor.bookplate.core.library

import androidx.room3.Room
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val libraryStorageModule: Module = module {
    single {
        val context = androidContext()
        Room.databaseBuilder(
            context = context,
            name = context.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
            factory = LibraryDatabaseConstructor::initialize,
        ).buildLibraryDatabase()
    }
    single {
        CoverStore(FileSystem.SYSTEM, File(androidContext().filesDir, COVERS_DIRECTORY_NAME).absolutePath.toPath())
    }
}
