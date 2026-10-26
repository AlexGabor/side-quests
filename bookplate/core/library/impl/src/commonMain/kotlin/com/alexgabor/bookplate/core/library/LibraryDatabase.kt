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
    version = 3,
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

/** Version 3 gives each book its own inks: every existing book gets a pair from [picker]. */
internal fun migration2To3(picker: BookInkPicker) = Migration(2, 3) { connection ->
    connection.execSQL("ALTER TABLE books ADD COLUMN ink_accent TEXT NOT NULL DEFAULT 'Purple'")
    connection.execSQL("ALTER TABLE books ADD COLUMN ink_accent_variant TEXT NOT NULL DEFAULT 'Fluorescent Pink'")
    val ids = connection.prepare("SELECT id FROM books").use { statement ->
        buildList { while (statement.step()) add(statement.getLong(0)) }
    }
    connection.prepare("UPDATE books SET ink_accent = ?, ink_accent_variant = ? WHERE id = ?").use { statement ->
        for (id in ids) {
            val inks = picker.pick()
            statement.bindText(1, inks.accent)
            statement.bindText(2, inks.accentVariant)
            statement.bindLong(3, id)
            statement.step()
            statement.reset()
        }
    }
}

/**
 * The settings every platform's database shares: the bundled SQLite, the migrations and queries on IO. [picker]
 * gives the books already in the library their inks when migrating to version 3.
 */
internal fun RoomDatabase.Builder<LibraryDatabase>.buildLibraryDatabase(picker: BookInkPicker): LibraryDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(Migration1To2, migration2To3(picker))
        .setQueryCoroutineContext(CoroutineDispatchers.IO)
        .build()
