package com.alexgabor.bookplate.feature.player

import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.FailureReason
import com.alexgabor.bookplate.core.library.FakeBookAvailability
import com.alexgabor.bookplate.core.library.FakeBookInkPicker
import com.alexgabor.bookplate.core.library.FakeBookRelinker
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.RelinkResult
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.bookplate.core.player.FakeBookPlayer
import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Player for a book whose files can't be reached: the unavailable state, checking again, and relinking. */
class PlayerRelinkTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()
    private val availability = FakeBookAvailability()
    private val relinker = FakeBookRelinker()
    private val moved = PickedDocument(DocumentRef("Books/Moved", null, isFolder = true), "Moved")

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    private fun addSampleBook(position: Duration = 42.seconds, isFolder: Boolean = true): BookId = repository.add(
        fakeBook(
            "Sample Book",
            author = "Sample Author",
            duration = 190.seconds,
            position = position,
            speed = 1.5f,
            source = DocumentRef("Books/Sample Book", null, isFolder = isFolder),
        ),
        chapters = listOf(
            Chapter("Opening", 0.seconds, 65.seconds),
            Chapter("Disc 2", 65.seconds, 190.seconds, children = listOf(Chapter("Middle", 65.seconds, 190.seconds))),
        ),
    )

    private fun TestScope.player() = FakeBookPlayer(repository, availability, backgroundScope)

    private fun TestScope.screen(id: BookId, player: FakeBookPlayer = player()) =
        PlayerScreenState(backgroundScope, id, player, repository, availability, relinker, FakeBookInkPicker()).also { runCurrent() }

    private val PlayerScreenState.shown: PlayerUi get() = assertNotNull(ui.value)

    /** Queues [result] on the relinker; a relink that fixes the book makes it available, as the real one checks. */
    private fun relinkTo(id: BookId, result: RelinkResult) = relinker.enqueue(result) {
        if (result is RelinkResult.Relinked || result is RelinkResult.Unchanged) availability.set(id, Availability.Available, publish = true)
    }

    // Unavailable state

    @Test
    fun openingChecksTheBook() = rule.testScope.runTest {
        val id = addSampleBook()

        screen(id)

        assertEquals(listOf(id), availability.checks)
    }

    @Test
    fun aMissingBookShowsFilesMissingInsteadOfTheControls() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)

        val ui = screen(id).shown

        assertEquals("Files missing", ui.unavailable?.title)
        assertEquals(
            "Bookplate can't find this book's files. If you moved or renamed them, relink the book to where they are now.",
            ui.unavailable?.message,
        )
        assertEquals("Sample Book", ui.title)
        assertTrue(ui.needsFolder)
    }

    @Test
    fun anAccessLostBookSaysSo() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.AccessLost)

        val ui = screen(id).shown

        assertEquals("Access lost", ui.unavailable?.title)
        assertEquals(
            "Bookplate is no longer allowed to open this book's files. Relink the book by picking them again.",
            ui.unavailable?.message,
        )
    }

    @Test
    fun anAvailableBookHasNoUnavailableState() = rule.testScope.runTest {
        assertNull(screen(addSampleBook()).shown.unavailable)
    }

    @Test
    fun nothingPlaysOrMovesWhileUnavailable() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val player = player()
        val screen = screen(id, player)

        screen.onPlayPause()
        screen.onSkipForward()
        screen.onChapterClick(1)
        screen.onSpeedChange(2f)
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(42.seconds, repository.all.single().book.position)
        assertEquals(1.5f, repository.all.single().book.speed)
        assertEquals("0:42", screen.shown.positionLabel)
    }

    @Test
    fun checkingAgainAfterTheFilesCameBackShowsTheBook() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        assertNotNull(screen.shown.unavailable)

        availability.set(id, Availability.Available)
        screen.onCheckAgain()
        runCurrent()

        assertNull(screen.shown.unavailable)
        assertEquals("0:42", screen.shown.positionLabel)
        assertEquals(listOf(id, id), availability.checks)
    }

    @Test
    fun aFileGoingMissingDuringPlaybackShowsFilesMissing() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        val screen = screen(id, player)
        screen.onPlayPause()
        runCurrent()
        assertTrue(screen.shown.isPlaying)

        availability.set(id, Availability.Missing)
        player.failPlayback()
        runCurrent()

        assertFalse(screen.shown.isPlaying)
        assertEquals("Files missing", screen.shown.unavailable?.title)
    }

    @Test
    fun aSingleFileBookRelinksWithAFile() = rule.testScope.runTest {
        val id = addSampleBook(isFolder = false)
        availability.set(id, Availability.Missing)

        assertFalse(screen(id).shown.needsFolder)
    }

    // Relinking

    @Test
    fun relinkingAMovedFolderShowsTheBookWhereItWas() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinkTo(id, RelinkResult.Relinked(emptyList()))

        screen.onRelinkPicked(moved)
        assertEquals(RelinkStatus.Running("Looking for the book's files in “Moved”…"), screen.relink.value)
        runCurrent()

        assertEquals(RelinkStatus.Finished("Relinked “Sample Book”", isError = false), screen.relink.value)
        assertNull(screen.shown.unavailable)
        assertEquals("0:42", screen.shown.positionLabel)
        assertEquals("1.5×", screen.shown.speedLabel)
        assertEquals("Opening", screen.shown.chapterTitle)
        assertEquals(listOf(FakeBookRelinker.Call(id, moved, emptySet())), relinker.calls)
    }

    @Test
    fun aRelinkedBookPlaysAgain() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val player = player()
        val screen = screen(id, player)
        relinkTo(id, RelinkResult.Relinked(emptyList()))
        screen.onRelinkPicked(moved)
        runCurrent()

        screen.onPlayPause()
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(42.seconds, player.state.value.position)
    }

    @Test
    fun aPartialMatchAsksAndRelinksAnywayWhenConfirmed() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.NeedsConfirmation(found = 1, total = 2, missing = listOf("Disc 2/02 - Middle.mp3")))
        relinkTo(id, RelinkResult.Relinked(leftOut = listOf("Disc 2/02 - Middle.mp3")))

        screen.onRelinkPicked(moved)
        runCurrent()

        val asked = assertIs<RelinkStatus.NeedsConfirmation>(screen.relink.value)
        assertEquals(
            "Found 1 of 2 files in “Moved”. Not found: Disc 2/02 - Middle.mp3. " +
                "Parts of the book without their files will be skipped.",
            asked.message,
        )
        assertNotNull(screen.shown.unavailable)

        screen.onRelinkConfirmed()
        runCurrent()

        assertEquals(setOf("Disc 2/02 - Middle.mp3"), relinker.calls.last().confirmMissing)
        assertEquals(
            RelinkStatus.Finished("Relinked “Sample Book”. 1 missing file will be skipped.", isError = false),
            screen.relink.value,
        )
        assertNull(screen.shown.unavailable)
    }

    @Test
    fun cancellingAPartialMatchChangesNothing() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.NeedsConfirmation(1, 2, listOf("Disc 2/02 - Middle.mp3")))
        screen.onRelinkPicked(moved)
        runCurrent()

        screen.onRelinkCancelled()
        runCurrent()

        assertNull(screen.relink.value)
        assertEquals("Files missing", screen.shown.unavailable?.title)
        assertEquals(1, relinker.calls.size)
    }

    @Test
    fun severalLeftOutFilesAreCounted() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinkTo(id, RelinkResult.Relinked(leftOut = listOf("a.mp3", "b.mp3")))

        screen.onRelinkPicked(moved)
        runCurrent()

        assertEquals("Relinked “Sample Book”. 2 missing files will be skipped.", screen.relink.value?.message)
    }

    @Test
    fun theWrongFolderSaysNoneOfTheFilesAreThere() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.NoMatch)

        screen.onRelinkPicked(moved)
        runCurrent()

        assertEquals(RelinkStatus.Finished("None of this book's files are in “Moved”", isError = true), screen.relink.value)
        assertEquals("Files missing", screen.shown.unavailable?.title)
    }

    @Test
    fun theWrongKindSaysWhatToPick() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.WrongKind(needsFolder = true))
        relinker.enqueue(RelinkResult.WrongKind(needsFolder = false))

        screen.onRelinkPicked(moved)
        runCurrent()
        assertEquals("“Sample Book” was added from a folder. Pick its folder.", screen.relink.value?.message)
        screen.onRelinkPicked(moved)
        runCurrent()
        assertEquals("“Sample Book” was added from a single file. Pick the file.", screen.relink.value?.message)
    }

    @Test
    fun anotherBooksFolderNamesThatBook() = rule.testScope.runTest {
        val id = addSampleBook()
        val other = repository.add(fakeBook("Other"))
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.OtherBook(other))

        screen.onRelinkPicked(moved)
        runCurrent()

        assertEquals(RelinkStatus.Finished("“Moved” is already the book “Other”", isError = true), screen.relink.value)
    }

    @Test
    fun unchangedAndFailedResults() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.Failed(FailureReason.SourceMissing))
        relinkTo(id, RelinkResult.Unchanged)

        screen.onRelinkPicked(moved)
        runCurrent()
        assertEquals(RelinkStatus.Finished("Couldn't open “Moved”. The file or folder is gone.", isError = true), screen.relink.value)
        screen.onRelinkPicked(moved)
        runCurrent()
        assertEquals(RelinkStatus.Finished("“Sample Book” is already linked to “Moved”", isError = false), screen.relink.value)
    }

    @Test
    fun aCancelledPickChangesNothing() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)

        screen.onRelinkPicked(null)
        runCurrent()

        assertNull(screen.relink.value)
        assertTrue(relinker.calls.isEmpty())
    }

    @Test
    fun picksAreIgnoredWhileARelinkRuns() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.NoMatch)

        screen.onRelinkPicked(moved)
        screen.onRelinkPicked(moved)
        runCurrent()

        assertEquals(1, relinker.calls.size)
    }

    @Test
    fun closingAResultClearsIt() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val screen = screen(id)
        relinker.enqueue(RelinkResult.NoMatch)
        screen.onRelinkPicked(moved)
        runCurrent()

        screen.onRelinkStatusClosed()

        assertNull(screen.relink.value)
    }
}
