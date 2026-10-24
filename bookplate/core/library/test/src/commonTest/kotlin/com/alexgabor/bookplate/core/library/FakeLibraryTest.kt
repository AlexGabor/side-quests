package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FakeLibraryTest {

    private val repository = FakeLibraryRepository()
    private val importer = FakeBookImporter(repository)
    private val pick = PickedDocument(DocumentRef("Books/Dune", null, isFolder = true), "Dune")

    @Test
    fun ordersByUpdatedAndFilters() = runTest {
        val dune = repository.add(fakeBook("Dune", author = "Frank Herbert", importedAt = at(1)))
        repository.add(fakeBook("Emma", author = "Jane Austen", importedAt = at(2)))

        assertEquals(listOf("Emma", "Dune"), repository.books().first().map { it.title })
        repository.setLastPlayed(dune, at(3))
        assertEquals(listOf("Dune", "Emma"), repository.books().first().map { it.title })
        assertEquals(listOf("Emma"), repository.books("AUSTEN").first().map { it.title })
        assertEquals(dune, repository.lastPlayed.first()?.id)
    }

    @Test
    fun noLastPlayedUntilPlayed() = runTest {
        repository.add(fakeBook("Dune"))
        assertNull(repository.lastPlayed.first())
    }

    @Test
    fun updatesPositionAndSpeed() = runTest {
        val id = repository.add(fakeBook("Dune"))
        repository.setPosition(id, 90.seconds)
        repository.setSpeed(id, 1.5f)
        val book = repository.book(id).first()!!.book
        assertEquals(90.seconds, book.position)
        assertEquals(1.5f, book.speed)
    }

    @Test
    fun scriptedResults() = runTest {
        importer.enqueue(ImportResult.AlreadyInLibrary(BookId(7)))
        importer.enqueueBook(fakeBook("Dune"), progress = listOf(ImportProgress.Listing))

        assertEquals(ImportResult.AlreadyInLibrary(BookId(7)), importer.import(pick))
        assertEquals(emptyList(), repository.all)

        val progress = mutableListOf<ImportProgress>()
        val result = importer.import(pick) { progress += it }
        assertEquals(ImportResult.Success(repository.all.single().book.id), result)
        assertEquals(listOf<ImportProgress>(ImportProgress.Listing), progress)
        assertEquals(listOf(pick, pick), importer.imported)
    }

    private fun at(seconds: Long) = Instant.fromEpochMilliseconds(seconds * 1000)
}
