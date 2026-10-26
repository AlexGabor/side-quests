package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentEntry
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.DocumentSource
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.Reachability
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class BookAvailabilityTest {

    private val rule = TestScopeRule()
    private lateinit var library: LibraryHarness

    private val documents get() = library.documents
    private val availability get() = library.availability

    @BeforeTest
    fun setUp() {
        rule.install()
        library = LibraryHarness()
    }

    @AfterTest
    fun tearDown() {
        library.close()
        rule.reset()
    }

    private fun addSampleBook(at: String = "Books/Sample Book") {
        documents.addFile("$at/01 - Opening.mp3", taggedMp3("Opening", album = "Sample Book"))
        documents.addFile("$at/Disc 2/02 - Middle.mp3", untaggedMp3())
    }

    @Test
    fun aBookWhoseFilesAreThereIsAvailable() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        assertEquals(Availability.Available, availability.check(id))
        assertEquals(mapOf(id to Availability.Available), availability.availability.value)
    }

    @Test
    fun aBookNotCheckedYetCountsAsAvailable() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        assertEquals(Availability.Available, availability.availability.value.of(id))
    }

    @Test
    fun aMovedFolderIsMissing() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        documents.rename("Books/Sample Book", "Books/Moved")

        assertEquals(Availability.Missing, availability.check(id))
        // The source answered; its files weren't asked.
        assertEquals(1, documents.reachabilityChecks)
    }

    @Test
    fun aDeletedFileIsMissing() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        documents.delete("Books/Sample Book/Disc 2/02 - Middle.mp3")

        assertEquals(Availability.Missing, availability.check(id))
    }

    @Test
    fun aFileRenamedInsideTheFolderIsMissing() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        documents.rename("Books/Sample Book/01 - Opening.mp3", "Books/Sample Book/01 - Intro.mp3")

        assertEquals(Availability.Missing, availability.check(id))
    }

    @Test
    fun aRevokedGrantIsAccessLostNotMissing() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")

        documents.revoke("Books/Sample Book")

        assertEquals(Availability.AccessLost, availability.check(id))
    }

    @Test
    fun accessLostWinsOverMissing() = rule.testScope.runTest {
        // The folder is still reachable, but one file is gone and another lost: a single-file grant elsewhere.
        addSampleBook()
        val id = library.import("Books/Sample Book")
        val details = library.details(id)
        documents.delete("Books/Sample Book/01 - Opening.mp3")
        val lost = details.files[1].ref.copy(root = "Elsewhere")
        val access = object : DocumentAccess by documents {
            override suspend fun reachability(ref: DocumentRef): Reachability =
                if (ref == details.files[1].ref) documents.reachability(lost) else documents.reachability(ref)
        }

        assertEquals(Availability.AccessLost, BookAvailabilityImpl(library.repository, access).check(id))
    }

    @Test
    fun aPickedFolderRenamedWhereBookmarksFollowStaysAvailable() = rule.testScope.runTest {
        library.close()
        library = LibraryHarness(FakeDocumentAccess(followsRenames = true))
        addSampleBook()
        val id = library.import("Books/Sample Book")

        documents.rename("Books/Sample Book", "Books/Renamed")

        assertEquals(Availability.Available, availability.check(id))
    }

    @Test
    fun aBookWhoseFilesComeBackIsAvailableAgainWithoutARelink() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")
        documents.rename("Books/Sample Book", "Books/Moved")
        assertEquals(Availability.Missing, availability.check(id))

        documents.rename("Books/Moved", "Books/Sample Book")

        assertEquals(Availability.Available, availability.check(id))
        assertEquals(Availability.Available, availability.availability.value.of(id))
    }

    @Test
    fun filesLeftOutByAPartialRelinkAreNotChecked() = rule.testScope.runTest {
        addSampleBook()
        val id = library.import("Books/Sample Book")
        documents.rename("Books/Sample Book", "Books/Moved")
        documents.delete("Books/Moved/Disc 2")
        val pick = library.pick("Books/Moved")
        library.relinker.relink(id, pick, confirmMissing = setOf("Disc 2/02 - Middle.mp3"))

        assertEquals(Availability.Available, availability.check(id))
        assertTrue(library.details(id).files[1].isMissing)
    }

    @Test
    fun aBookNoLongerInTheLibraryIsDropped() = rule.testScope.runTest {
        assertEquals(Availability.Available, availability.check(BookId(42)))
        assertEquals(emptyMap(), availability.availability.value)
    }

    @Test
    fun checkingTheLibraryChecksEveryBookAtMostFourAtATimeOnIo() = rule.testScope.runTest {
        val dispatchers = mutableListOf<ContinuationInterceptor?>()
        val watched = object : DocumentAccess {
            override suspend fun list(ref: DocumentRef): List<DocumentEntry> = library.documents.list(ref)
            override suspend fun open(ref: DocumentRef): DocumentSource = library.documents.open(ref)
            override suspend fun reachability(ref: DocumentRef): Reachability {
                dispatchers += currentCoroutineContext()[ContinuationInterceptor]
                return library.documents.reachability(ref)
            }
        }
        library.close()
        library = LibraryHarness(FakeDocumentAccess(), watched)
        val ids = (1..6).map { n ->
            documents.addFile("Books/Book $n/01.mp3", taggedMp3("One", album = "Book $n"))
            library.import("Books/Book $n")
        }
        documents.delete("Books/Book 3")
        documents.checkDelay = 100.milliseconds

        // Started from the main dispatcher, as Home does.
        val job = launch(CoroutineDispatchers.Main) { availability.checkAll() }
        runCurrent()
        // Results arrive while the check runs: the first four books' sources are answered after one delay.
        testScheduler.advanceTimeBy(150)
        runCurrent()
        assertTrue(availability.availability.value.size in 1..5, "${availability.availability.value}")
        job.join()

        assertEquals(ids.toSet(), availability.availability.value.keys)
        assertEquals(Availability.Missing, availability.availability.value[ids[2]])
        assertEquals(5, availability.availability.value.values.count { it == Availability.Available })
        assertEquals(4, documents.peakConcurrentChecks)
        assertTrue(dispatchers.all { it === CoroutineDispatchers.IO }, "$dispatchers")
    }

    @Test
    fun aSecondCheckOfTheLibraryWhileOneRunsDoesNothing() = rule.testScope.runTest {
        addSampleBook()
        library.import("Books/Sample Book")
        documents.checkDelay = 100.milliseconds

        val first = launch { availability.checkAll() }
        runCurrent()
        availability.checkAll()
        first.join()

        // The book's source and its two files, once.
        assertEquals(3, documents.reachabilityChecks)
    }
}
