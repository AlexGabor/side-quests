package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class LibraryRepositoryImplTest {

    private val rule = TestScopeRule()
    private val documents = FakeDocumentAccess()
    private val clock = FixedClock()
    private val coversDirectory = temporaryDirectory()
    private lateinit var database: LibraryDatabase
    private lateinit var importer: BookImporterImpl
    private lateinit var repository: LibraryRepositoryImpl

    @BeforeTest
    fun setUp() {
        rule.install()
        database = inMemoryLibraryDatabase()
        val covers = CoverStore(FileSystem.SYSTEM, coversDirectory)
        importer = BookImporterImpl(database.dao(), covers, documents, clock)
        repository = LibraryRepositoryImpl(database.dao(), covers)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        rule.reset()
        deleteDirectory(coversDirectory)
    }

    @Test
    fun recentlyUpdatedFirst() = rule.testScope.runTest {
        val dune = add("Dune", author = "Frank Herbert")
        add("Emma", author = "Jane Austen")
        assertEquals(listOf("Emma", "Dune"), titles())

        clock.now = clock.now.plus(10.seconds)
        repository.setLastPlayed(dune, clock.now)
        assertEquals(listOf("Dune", "Emma"), titles())
    }

    @Test
    fun playedBeforeALaterImportStaysBehindIt() = rule.testScope.runTest {
        val dune = add("Dune")
        repository.setLastPlayed(dune, clock.now.plus(1.seconds))
        clock.now = clock.now.plus(5.seconds)
        add("Emma")

        assertEquals(listOf("Emma", "Dune"), titles())
    }

    @Test
    fun filtersByTitleOrAuthorIgnoringCase() = rule.testScope.runTest {
        add("Dune", author = "Frank Herbert")
        add("Emma", author = "Jane Austen")
        add("Ünïcødé Tales", author = null)

        assertEquals(listOf("Emma"), titles("austen"))
        assertEquals(listOf("Dune"), titles("UNE"))
        assertEquals(listOf("Ünïcødé Tales"), titles("ünï"))
        assertEquals(3, titles("  ").size)
        assertEquals(emptyList(), titles("nothing"))
    }

    @Test
    fun lastPlayed() = rule.testScope.runTest {
        val dune = add("Dune")
        val emma = add("Emma")
        assertNull(repository.lastPlayed.first())

        repository.setLastPlayed(emma, clock.now.plus(1.seconds))
        repository.setLastPlayed(dune, clock.now.plus(2.seconds))
        assertEquals(dune, repository.lastPlayed.first()?.id)
        assertEquals(clock.now.plus(2.seconds), repository.lastPlayed.first()?.lastPlayedAt)
    }

    @Test
    fun positionAndSpeed() = rule.testScope.runTest {
        val dune = add("Dune")
        val emma = add("Emma")

        repository.setPosition(dune, 90.seconds)
        repository.setSpeed(dune, 1.5f)

        val book = repository.book(dune).first()!!.book
        assertEquals(90.seconds, book.position)
        assertEquals(1.5f, book.speed)
        val other = repository.book(emma).first()!!.book
        assertEquals(0.seconds, other.position)
        assertEquals(1f, other.speed)
    }

    @Test
    fun unknownBook() = rule.testScope.runTest {
        assertNull(repository.book(BookId(42)).first())
    }

    @Test
    fun flowsFollowChanges() = rule.testScope.runTest {
        val seen = mutableListOf<List<String>>()
        val job = launch { repository.books().collect { books -> seen += books.map { it.title } } }
        settle()

        add("Dune")
        settle()
        add("Emma")
        settle()

        job.cancel()
        assertEquals(listOf(emptyList(), listOf("Dune"), listOf("Emma", "Dune")), seen.distinct())
    }

    private suspend fun TestScope.settle() = testScheduler.run { advanceUntilIdle(); runCurrent() }

    private suspend fun titles(query: String = "") = repository.books(query).first().map { it.title }

    /** Imports a one-file book tagged [title] and [author], one second after the previous one. */
    private suspend fun add(title: String, author: String? = null): BookId {
        clock.now = clock.now.plus(1.seconds)
        documents.addFile("Books/$title/01.mp3", taggedMp3(title, artist = author, album = title))
        val result = importer.import(PickedDocument(documents.grant("Books/$title"), title))
        return assertIs<ImportResult.Success>(result).bookId
    }
}
