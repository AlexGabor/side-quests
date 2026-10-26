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

    @Test
    fun scriptedAvailability() = runTest {
        val availability = FakeBookAvailability()
        val dune = BookId(1)
        availability.set(dune, Availability.Missing)

        assertEquals(Availability.Missing, availability.check(dune))
        assertEquals(Availability.Missing, availability.availability.value.of(dune))
        assertEquals(Availability.Available, availability.check(BookId(2)))
        assertEquals(listOf(dune, BookId(2)), availability.checks)
        availability.checkAll()
        assertEquals(1, availability.checkAllCount)
    }

    @Test
    fun scriptedRelinks() = runTest {
        val relinker = FakeBookRelinker()
        var ran = false
        relinker.enqueue(RelinkResult.NoMatch)
        relinker.enqueue(RelinkResult.Relinked(emptyList())) { ran = true }

        assertEquals(RelinkResult.NoMatch, relinker.relink(BookId(1), pick))
        assertEquals(false, ran)
        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(BookId(1), pick, setOf("a.mp3")))
        assertEquals(true, ran)
        assertEquals(setOf("a.mp3"), relinker.calls.last().confirmMissing)
    }

    private fun at(seconds: Long) = Instant.fromEpochMilliseconds(seconds * 1000)
}
