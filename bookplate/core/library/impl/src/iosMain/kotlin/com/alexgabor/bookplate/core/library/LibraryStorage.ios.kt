package com.alexgabor.bookplate.core.library

import androidx.room3.Room
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual val libraryStorageModule: Module = module {
    single {
        val directory = applicationSupport()
        // Application Support doesn't exist until an app puts something in it.
        FileSystem.SYSTEM.createDirectories(directory)
        Room.databaseBuilder(
            name = (directory / DATABASE_FILE_NAME).toString(),
            factory = LibraryDatabaseConstructor::initialize,
        ).buildLibraryDatabase(get())
    }
    single { CoverStore(FileSystem.SYSTEM, applicationSupport() / COVERS_DIRECTORY_NAME) }
}

private fun applicationSupport(): Path = (
    NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
    ).toPath()
