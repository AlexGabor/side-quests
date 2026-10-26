package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import okio.FileSystem

/**
 * The real library over an in-memory Room database and [documents]: importer, repository, availability and
 * relinker, wired as `libraryModule` wires them. Build it after `TestScopeRule.install()`, and [close] it.
 *
 * [access] is what the library reads through; it defaults to [documents], and a test may wrap it to watch calls.
 */
internal class LibraryHarness(
    val documents: FakeDocumentAccess = FakeDocumentAccess(),
    access: DocumentAccess = documents,
) {
    val clock = FixedClock()
    private val coversDirectory = temporaryDirectory()
    val covers = CoverStore(FileSystem.SYSTEM, coversDirectory)
    val database = inMemoryLibraryDatabase()
    val repository = LibraryRepositoryImpl(database.dao(), covers)
    val importer = BookImporterImpl(database.dao(), covers, access, clock)
    val availability = BookAvailabilityImpl(repository, access)
    val relinker = BookRelinkerImpl(database.dao(), access, availability)

    /** Grants [path] as a fresh pick of it, named like the platform would name it. */
    fun pick(path: String): PickedDocument = PickedDocument(documents.grant(path), path.substringAfterLast('/'))

    suspend fun import(path: String): BookId = assertIs<ImportResult.Success>(importer.import(pick(path))).bookId

    suspend fun details(id: BookId): BookDetails = checkNotNull(repository.book(id).first())

    fun close() {
        database.close()
        deleteDirectory(coversDirectory)
    }
}
