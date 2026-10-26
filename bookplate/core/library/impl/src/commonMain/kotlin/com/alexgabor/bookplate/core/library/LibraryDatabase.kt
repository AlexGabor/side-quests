package com.alexgabor.bookplate.core.library

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alexgabor.lib.coroutine.CoroutineDispatchers

@Database(
    entities = [BookEntity::class, AudioFileEntity::class, ChapterEntity::class],
    version = 2,
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

/** Version 2 marks the files a partial relink left out; every existing file is in. */
internal val Migration1To2 = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE audio_files ADD COLUMN missing INTEGER NOT NULL DEFAULT 0")
}

/** The settings every platform's database shares: the bundled SQLite, the migrations and queries on IO. */
internal fun RoomDatabase.Builder<LibraryDatabase>.buildLibraryDatabase(): LibraryDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(Migration1To2)
        .setQueryCoroutineContext(CoroutineDispatchers.IO)
        .build()
