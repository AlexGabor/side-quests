package com.alexgabor.bookplate.feature.home

import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.bookplate.core.library.BookImporter
import com.alexgabor.bookplate.core.library.FailureReason
import com.alexgabor.bookplate.core.library.FakeBookAvailability
import com.alexgabor.bookplate.core.library.FakeBookImporter
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.ImportProgress
import com.alexgabor.bookplate.core.library.ImportResult
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class HomeStateTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()
    private val importer = FakeBookImporter(repository)
    private val availability = FakeBookAvailability()

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    private fun TestScope.home(query: String = ""): HomeState =
        HomeState(backgroundScope, repository, importer, availability, query = query).also { runCurrent() }

    private val HomeState.rows: List<BookRow>
        get() = (library.value as Library.Books).rows

    private fun pick(name: String) = PickedDocument(DocumentRef(root = "Books/$name", child = null, isFolder = true), name)

    // Library

    @Test
    fun anEmptyLibraryShowsTheEmptyState() = rule.testScope.runTest {
        assertEquals(Library.Empty, home().library.value)
    }

    @Test
    fun rowsShowTitleAuthorDurationAndProgress() = rule.testScope.runTest {
        repository.add(fakeBook("Dune", author = "Frank Herbert", duration = 2.hours, position = 30.minutes, coverPath = "/covers/dune.png"))
        repository.add(fakeBook("Single", duration = 42.seconds))

        val rows = home().rows.associateBy { it.title }

        assertEquals("Frank Herbert", rows.getValue("Dune").author)
        assertEquals("2:00:00", rows.getValue("Dune").duration)
        assertEquals("25%", rows.getValue("Dune").percent)
        assertEquals(0.25f, rows.getValue("Dune").progress)
        assertEquals("/covers/dune.png", rows.getValue("Dune").coverPath)
        assertEquals("Unknown author", rows.getValue("Single").author)
        assertEquals("0:42", rows.getValue("Single").duration)
        assertEquals("0%", rows.getValue("Single").percent)
        assertNull(rows.getValue("Single").coverPath)
    }

    @Test
    fun rowsCarryTheirBooksInksAndFollowChanges() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune", importedAt = Instant.fromEpochSeconds(1), inks = BookInks("Teal", "Yellow")))
        repository.add(fakeBook("Emma", importedAt = Instant.fromEpochSeconds(2), inks = BookInks("Red", "Blue")))
        val home = home()
        assertEquals(listOf(BookInks("Red", "Blue"), BookInks("Teal", "Yellow")), home.rows.map { it.inks })

        repository.setInks(dune, BookInks("Green", "Orange"))
        runCurrent()

        assertEquals(listOf(BookInks("Red", "Blue"), BookInks("Green", "Orange")), home.rows.map { it.inks })
    }

    @Test
    fun percentIsRoundedDownAndAZeroDurationIsZero() = rule.testScope.runTest {
        repository.add(fakeBook("Almost", duration = 100.minutes, position = 99.minutes + 59.seconds))
        repository.add(fakeBook("Empty", duration = 0.seconds, position = 5.seconds))

        val rows = home().rows.associateBy { it.title }

        assertEquals("99%", rows.getValue("Almost").percent)
        assertEquals("0%", rows.getValue("Empty").percent)
        assertEquals(0f, rows.getValue("Empty").progress)
    }

    @Test
    fun rowsAreMostRecentlyUpdatedFirstAndFollowTheLibrary() = rule.testScope.runTest {
        val a = repository.add(fakeBook("A", importedAt = Instant.fromEpochSeconds(1)))
        repository.add(fakeBook("B", importedAt = Instant.fromEpochSeconds(2)))
        val home = home()
        assertEquals(listOf("B", "A"), home.rows.map { it.title })

        repository.setLastPlayed(a, Instant.fromEpochSeconds(3))
        runCurrent()

        assertEquals(listOf("A", "B"), home.rows.map { it.title })
    }

    // Search

    @Test
    fun searchFiltersByAuthorAndClearingShowsAll() = rule.testScope.runTest {
        repository.add(fakeBook("Dune", author = "Frank Herbert"))
        repository.add(fakeBook("Emma", author = "Jane Austen"))
        val home = home()

        home.onQueryChange("austen")
        runCurrent()
        assertEquals("austen", home.query.value)
        assertEquals(listOf("Emma"), home.rows.map { it.title })

        home.onQueryChange("")
        runCurrent()
        assertEquals(2, home.rows.size)
    }

    @Test
    fun aSearchWithNoMatchesShowsNoResults() = rule.testScope.runTest {
        repository.add(fakeBook("Dune"))
        val home = home()

        home.onQueryChange("zzz ")
        runCurrent()

        assertEquals(Library.NoResults("zzz"), home.library.value)
    }

    @Test
    fun aRestoredQueryFiltersFromTheStart() = rule.testScope.runTest {
        repository.add(fakeBook("Dune"))
        repository.add(fakeBook("Emma"))

        assertEquals(listOf("Emma"), home(query = "emm").rows.map { it.title })
    }

    // Adding

    @Test
    fun theAddSheetOpensAndCloses() = rule.testScope.runTest {
        val home = home()

        home.onAddClick()
        assertTrue(home.addSheetOpen.value)

        home.onAddSheetDismissed()
        assertFalse(home.addSheetOpen.value)
    }

    @Test
    fun aCancelledPickDoesNothing() = rule.testScope.runTest {
        val home = home()

        home.onPicked(null)
        runCurrent()

        assertNull(home.importStatus.value)
        assertTrue(importer.imported.isEmpty())
    }

    @Test
    fun aSuccessfulImportAddsTheBookAndNamesIt() = rule.testScope.runTest {
        val home = home()
        importer.enqueueBook(fakeBook("Sample Book", author = "Sample Author"))

        home.onPicked(pick("Sample"))
        runCurrent()

        assertEquals(ImportStatus.Finished("Sample", "Added “Sample Book”", isError = false), home.importStatus.value)
        assertEquals(listOf("Sample Book"), home.rows.map { it.title })
        assertEquals(listOf("Sample"), importer.imported.map { it.name })
    }

    @Test
    fun alreadyInLibraryNamesTheExistingBookAndChangesNothing() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Sample Book"))
        val home = home()
        importer.enqueue(ImportResult.AlreadyInLibrary(id))

        home.onPicked(pick("Sample"))
        runCurrent()

        assertEquals(
            ImportStatus.Finished("Sample", "“Sample Book” is already in your library", isError = false),
            home.importStatus.value,
        )
        assertEquals(1, repository.all.size)
    }

    @Test
    fun noAudioAndFailuresAreErrorsNamingThePick() = rule.testScope.runTest {
        val home = home()
        importer.enqueue(ImportResult.NoAudioFound)
        importer.enqueue(ImportResult.Failed(FailureReason.SourceMissing))

        home.onPicked(pick("Notes"))
        runCurrent()
        assertEquals(ImportStatus.Finished("Notes", "No audio found in “Notes”", isError = true), home.importStatus.value)

        home.onPicked(pick("Gone"))
        runCurrent()
        assertEquals(
            ImportStatus.Finished("Gone", "Couldn't add “Gone”. The file or folder is gone.", isError = true),
            home.importStatus.value,
        )
        assertEquals(Library.Empty, home.library.value)
    }

    @Test
    fun progressIsShownWhileTheImportRuns() = rule.testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        val reported = mutableListOf<ImportProgress>()
        val slowImporter = object : BookImporter {
            override suspend fun import(document: PickedDocument, onProgress: (ImportProgress) -> Unit): ImportResult {
                listOf(ImportProgress.Listing, ImportProgress.Reading(2, 3)).forEach { reported += it; onProgress(it) }
                gate.await()
                return ImportResult.NoAudioFound
            }
        }
        val home = HomeState(backgroundScope, repository, slowImporter, availability)

        home.onPicked(pick("Sample"))
        runCurrent()

        assertEquals(ImportStatus.Running("Sample", "Reading file 2 of 3", 2f / 3), home.importStatus.value)

        // A second pick while one runs is ignored.
        home.onPicked(pick("Other"))
        runCurrent()
        assertEquals("Sample", home.importStatus.value?.name)

        gate.complete(Unit)
        runCurrent()
        assertTrue(home.importStatus.value is ImportStatus.Finished)
        assertEquals(2, reported.size)
    }

    @Test
    fun closingTheResultClearsIt() = rule.testScope.runTest {
        val home = home()
        importer.enqueue(ImportResult.NoAudioFound)
        home.onPicked(pick("Notes"))
        runCurrent()

        home.onStatusClosed()

        assertNull(home.importStatus.value)
    }

    @Test
    fun anImporterThatThrowsIsShownAsAFailure() = rule.testScope.runTest {
        val home = home()
        // Nothing queued: the fake throws.
        home.onPicked(pick("Broken"))
        runCurrent()

        assertEquals(
            ImportStatus.Finished("Broken", "Couldn't add “Broken”. A file couldn't be read.", isError = true),
            home.importStatus.value,
        )
    }

    @Test
    fun durationsAreFormattedByHour() {
        assertEquals("0:42", formatDuration(42.seconds))
        assertEquals("1:05:00", formatDuration(65.minutes))
        assertEquals("21:02:00", formatDuration(21.hours + 2.minutes))
    }

    @Test
    fun bookIdsComeFromTheLibrary() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Dune"))
        assertEquals(listOf(id), home().rows.map { it.id })
        assertTrue(id != BookId(0))
    }

    // Unavailable books (M14)

    @Test
    fun creatingHomeChecksEveryBookOnce() = rule.testScope.runTest {
        repository.add(fakeBook("Dune"))

        home()

        assertEquals(1, availability.checkAllCount)
    }

    @Test
    fun aMissingBooksRowSaysSoAndOffersRelink() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune", importedAt = Instant.fromEpochSeconds(1)))
        repository.add(fakeBook("Emma", importedAt = Instant.fromEpochSeconds(2)))
        availability.set(dune, Availability.Missing)

        val rows = home().rows.associateBy { it.title }

        assertEquals("Files missing · Tap to relink", rows.getValue("Dune").unavailable)
        assertNull(rows.getValue("Emma").unavailable)
        // Everything else about the row stays.
        assertEquals("0%", rows.getValue("Dune").percent)
    }

    @Test
    fun anAccessLostBooksRowSaysSo() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune"))
        availability.set(dune, Availability.AccessLost)

        assertEquals("Access lost · Tap to relink", home().rows.single().unavailable)
    }

    @Test
    fun rowsShowAtOnceAndChangeAsChecksComeBack() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune"))
        val home = home()
        assertNull(home.rows.single().unavailable)

        availability.set(dune, Availability.Missing)
        availability.check(dune)
        runCurrent()

        assertEquals("Files missing · Tap to relink", home.rows.single().unavailable)
    }

    @Test
    fun aBookThatIsAvailableAgainLosesItsLine() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune"))
        availability.set(dune, Availability.Missing)
        val home = home()
        assertEquals("Files missing · Tap to relink", home.rows.single().unavailable)

        availability.set(dune, Availability.Available)
        availability.check(dune)
        runCurrent()

        assertNull(home.rows.single().unavailable)
    }

    @Test
    fun searchKeepsTheUnavailableLine() = rule.testScope.runTest {
        val dune = repository.add(fakeBook("Dune", author = "Frank Herbert"))
        availability.set(dune, Availability.Missing)
        val home = home()

        home.onQueryChange("herbert")
        runCurrent()

        assertEquals("Files missing · Tap to relink", home.rows.single().unavailable)
    }
}
