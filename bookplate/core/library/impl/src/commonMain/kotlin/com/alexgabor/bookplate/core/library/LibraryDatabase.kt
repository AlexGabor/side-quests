package com.alexgabor.bookplate.core.library

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alexgabor.lib.coroutine.CoroutineDispatchers

@Database(
    entities = [BookEntity::class, AudioFileEntity::class, ChapterEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(LibraryDatabaseConstructor::class)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

// Room generates the actual object for each platform.
@Suppress("KotlinNoActualForExpect")
internal expect object LibraryDatabaseConstructor : RoomDatabaseConstructor<LibraryDatabase> {
    override fun initialize(): LibraryDatabase
}

internal const val DATABASE_FILE_NAME = "library.db"
internal const val COVERS_DIRECTORY_NAME = "covers"

/** The settings every platform's database shares: the bundled SQLite and queries on IO. */
internal fun RoomDatabase.Builder<LibraryDatabase>.buildLibraryDatabase(): LibraryDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(CoroutineDispatchers.IO)
        .build()
