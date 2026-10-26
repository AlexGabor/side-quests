package com.alexgabor.bookplate.core.library

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.alexgabor.lib.coroutine.TestScopeRule
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem

/** A version 1 database, made from its exported schema, opens with this version and keeps its books. */
class LibraryMigrationTest {

    private val rule = TestScopeRule()
    private val directory = Files.createTempDirectory("bookplate-migration").toFile()
    private val file = File(directory, DATABASE_FILE_NAME)

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() {
        rule.reset()
        directory.deleteRecursively()
    }

    @Test
    fun version1MigratesKeepingEveryBookWithNoFileLeftOut() = rule.testScope.runTest {
        createVersion1()

        val database = Room.databaseBuilder(file.absolutePath, LibraryDatabaseConstructor::initialize).buildLibraryDatabase()
        val covers = CoverStore(FileSystem.SYSTEM, temporaryDirectory())
        val details = assertNotNull(LibraryRepositoryImpl(database.dao(), covers).book(BookId(1)).first())
        database.close()

        assertEquals("Dune", details.book.title)
        assertEquals(42.seconds, details.book.position)
        assertEquals(1.5f, details.book.speed)
        assertEquals(listOf("01.mp3", "02.mp3"), details.files.map { it.relativePath })
        assertEquals(listOf(false, false), details.files.map { it.isMissing })
        assertEquals(listOf("One"), details.chapters.map { it.title })
    }

    private fun createVersion1() {
        val schema = Json.parseToJsonElement(File("schemas/${LibraryDatabase::class.qualifiedName}/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = 1")
            val ref = "docref1;D;Books/Dune"
            connection.execSQL(
                "INSERT INTO books (id, source_ref, fingerprint, title, duration_ms, position_ms, speed, imported_at) " +
                    "VALUES (1, '$ref', 'f', 'Dune', 2000, 42000, 1.5, 0)",
            )
            connection.execSQL(
                "INSERT INTO audio_files (book_id, ordinal, source_ref, relative_path, size, duration_ms, start_ms) VALUES " +
                    "(1, 0, '$ref;01.mp3', '01.mp3', 10, 1000, 0), (1, 1, '$ref;02.mp3', '02.mp3', 10, 1000, 1000)",
            )
            connection.execSQL(
                "INSERT INTO chapters (book_id, parent_id, ordinal, title, start_ms, end_ms) VALUES (1, NULL, 0, 'One', 0, 2000)",
            )
        } finally {
            connection.close()
        }
    }
}
