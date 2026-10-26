package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.AudioFile
import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.FakeBookAvailability
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class EngineBookPlayerTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()
    private val availability = FakeBookAvailability()
    private val backgrounded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var now = Instant.fromEpochSeconds(1_000_000)
    private val clock = object : Clock {
        override fun now() = now
    }

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    /**
     * `Sample Book`: files `01 - Opening.mp3` (1:05) and `02 - Middle.mp3` (2:05); chapter `Opening` 0:00–1:05,
     * then section `Disc 2` holding `Middle` 1:05–3:10.
     */
    private fun addSampleBook(
        title: String = "Sample Book",
        position: Duration = Duration.ZERO,
        speed: Float = 1f,
    ): BookId = repository.add(
        fakeBook(title, author = "Sample Author", duration = 190.seconds, position = position, speed = speed),
        files = listOf(
            file(title, 0, "01 - Opening.mp3", start = Duration.ZERO, duration = 65.seconds),
            file(title, 1, "Disc 2/02 - Middle.mp3", start = 65.seconds, duration = 125.seconds),
        ),
        chapters = listOf(
            Chapter("Opening", 0.seconds, 65.seconds),
            Chapter("Disc 2", 65.seconds, 190.seconds, children = listOf(Chapter("Middle", 65.seconds, 190.seconds))),
        ),
    )

    private fun file(book: String, index: Int, path: String, start: Duration, duration: Duration) = AudioFile(
        ref = DocumentRef("Books/$book", child = path, isFolder = true),
        relativePath = path,
        size = null,
        duration = duration,
        index = index,
        start = start,
    )

    private fun TestScope.output() = FakeAudioOutput { testScheduler.currentTime.milliseconds }

    private fun TestScope.player(output: AudioOutput) =
        EngineBookPlayer(repository, availability, output, backgroundScope, clock, backgrounded)

    private fun saved(id: BookId) = repository.all.first { it.book.id == id }.book

    private suspend fun TestScope.status(output: FakeAudioOutput) = output.status().also { runCurrent() }

    @Test
    fun loadsABookPausedAtItsSavedPositionAndSpeed() = rule.testScope.runTest {
        val id = addSampleBook(position = 90.seconds, speed = 1.5f)
        val output = output()
        val player = player(output)

        player.load(id)
        runCurrent()

        val state = player.state.value
        assertEquals("Sample Book", state.book?.title)
        assertEquals(90.seconds, state.position)
        assertEquals(1.5f, state.speed)
        assertFalse(state.isPlaying)
        assertEquals(1, state.chapterIndex)
        assertEquals(listOf("Sample Book", "Sample Book"), output.files.map { it.title })
        assertEquals(FilePosition(1, 25.seconds), status(output).position)
        assertEquals(1.5f, output.speed)
        assertFalse(output.isPlaying)
    }

    @Test
    fun unknownBookLeavesNothingLoaded() = rule.testScope.runTest {
        val output = output()
        val player = player(output)
        player.load(addSampleBook())
        runCurrent()

        player.load(BookId(99))
        runCurrent()

        assertNull(player.state.value.book)
        assertTrue(output.files.isEmpty())
    }

    @Test
    fun playsAcrossAFileBoundary() = rule.testScope.runTest {
        val id = addSampleBook(position = 60.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        assertEquals(0, player.state.value.chapterIndex)

        advanceTimeBy(10.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(70.seconds, player.state.value.position)
        assertEquals(1, player.state.value.chapterIndex)
        assertEquals("Middle", player.state.value.chapter?.title)
        assertEquals(FilePosition(1, 5.seconds), status(output).position)
    }

    @Test
    fun playingAtSpeed() = rule.testScope.runTest {
        val id = addSampleBook(position = 10.seconds, speed = 1.5f)
        val player = player(output())
        player.load(id)
        player.play()
        runCurrent()

        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(25.seconds, player.state.value.position)
    }

    @Test
    fun skipForwardCrossesIntoTheNextFile() = rule.testScope.runTest {
        val id = addSampleBook(position = 60.seconds)
        val output = output()
        val player = player(output)
        player.load(id)

        player.skipForward()
        runCurrent()

        assertEquals(70.seconds, player.state.value.position)
        assertEquals(FilePosition(1, 5.seconds), output.seeks.last())
        assertEquals(70.seconds, saved(id).position)
    }

    @Test
    fun skipBackCrossesIntoThePreviousFile() = rule.testScope.runTest {
        val id = addSampleBook(position = 70.seconds)
        val output = output()
        val player = player(output)
        player.load(id)

        player.skipBack()
        runCurrent()

        assertEquals(60.seconds, player.state.value.position)
        assertEquals(FilePosition(0, 60.seconds), output.seeks.last())
    }

    @Test
    fun skipWhilePlayingStartsFromWhereTheAudioIs() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player(output())
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(3_100.milliseconds)

        player.skipForward()
        runCurrent()

        assertEquals(13_100.milliseconds, player.state.value.position)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun skipBackStopsAtTheStart() = rule.testScope.runTest {
        val id = addSampleBook(position = 4.seconds)
        val output = output()
        val player = player(output)
        player.load(id)

        player.skipBack()
        runCurrent()

        assertEquals(Duration.ZERO, player.state.value.position)
        assertEquals(FilePosition(0, Duration.ZERO), output.seeks.last())
    }

    @Test
    fun skipForwardStopsAtTheEndAndPauses() = rule.testScope.runTest {
        val id = addSampleBook(position = 185.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()

        player.skipForward()
        runCurrent()

        assertEquals(190.seconds, player.state.value.position)
        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(190.seconds, saved(id).position)
    }

    @Test
    fun seekIsClampedToTheBook() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)

        player.seekTo(500.seconds)
        runCurrent()
        assertEquals(190.seconds, player.state.value.position)
        assertEquals(FilePosition(1, 125.seconds), output.seeks.last())

        player.seekTo(90.seconds)
        runCurrent()
        assertEquals(FilePosition(1, 25.seconds), output.seeks.last())
        assertEquals(90.seconds, saved(id).position)
    }

    @Test
    fun jumpsToAChapterInAnotherFile() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)

        player.jumpToChapter(1)
        runCurrent()

        assertEquals(65.seconds, player.state.value.position)
        assertEquals(1, player.state.value.chapterIndex)
        assertEquals(FilePosition(1, Duration.ZERO), output.seeks.last())
        assertEquals(65.seconds, saved(id).position)
    }

    @Test
    fun jumpToAChapterOutOfRangeDoesNothing() = rule.testScope.runTest {
        val id = addSampleBook(position = 30.seconds)
        val output = output()
        val player = player(output)
        player.load(id)

        player.jumpToChapter(5)
        runCurrent()

        assertEquals(30.seconds, player.state.value.position)
        assertTrue(output.seeks.isEmpty())
    }

    @Test
    fun speedIsSnappedSetOnTheOutputAndSavedPerBook() = rule.testScope.runTest {
        val first = addSampleBook()
        val second = addSampleBook(title = "Other Book")
        val output = output()
        val player = player(output)
        player.load(first)

        player.setSpeed(1.26f)
        runCurrent()
        assertEquals(1.3f, player.state.value.speed)
        assertEquals(1.3f, output.speed)
        assertEquals(1.3f, saved(first).speed)

        player.load(second)
        runCurrent()
        assertEquals(1f, player.state.value.speed)
        assertEquals(1f, output.speed)

        player.load(first)
        runCurrent()
        assertEquals(1.3f, player.state.value.speed)
        assertEquals(1.3f, output.speed)
    }

    @Test
    fun savesEveryFiveSecondsWhilePlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player(output())
        player.load(id)
        player.play()
        runCurrent()

        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(Duration.ZERO, saved(id).position)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(5.seconds, saved(id).position)

        advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(10.seconds, saved(id).position)
    }

    @Test
    fun pauseSavesThePositionAndLastPlayed() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        assertEquals(now, saved(id).lastPlayedAt)
        assertEquals(id, repository.lastPlayed.first()?.id)

        advanceTimeBy(3.seconds)
        now += 3.seconds
        player.pause()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(3.seconds, player.state.value.position)
        assertEquals(3.seconds, saved(id).position)
        assertEquals(now, saved(id).lastPlayedAt)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3.seconds, player.state.value.position)
    }

    @Test
    fun followsAPauseThePlatformMakes() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(30.seconds)
        now += 30.seconds

        output.platformPause()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(30.seconds, player.state.value.position)
        assertEquals(30.seconds, saved(id).position)
        assertEquals(now, saved(id).lastPlayedAt)
    }

    @Test
    fun savesASeekThePlatformMakesWhilePaused() = rule.testScope.runTest {
        val id = addSampleBook(position = 30.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        runCurrent()

        output.platformSeek(FilePosition(1, 55.seconds))
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(120.seconds, player.state.value.position)
        assertEquals(1, player.state.value.chapterIndex)
        assertEquals(120.seconds, saved(id).position)
    }

    @Test
    fun savesASeekThePlatformMakesWhilePlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(1.seconds)

        output.platformSeek(FilePosition(0, 40.seconds))
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(40.seconds, player.state.value.position)
        assertEquals(40.seconds, saved(id).position)
    }

    @Test
    fun followsPlaybackThePlatformResumes() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(10.seconds)
        output.platformPause()
        runCurrent()

        output.platformPlay()
        runCurrent()
        advanceTimeBy(5.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(15.seconds, player.state.value.position)
    }

    @Test
    fun resumesAfterATransientFocusLoss() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(10.seconds)

        output.platformSuppress(true)
        runCurrent()
        assertFalse(player.state.value.isPlaying)
        assertEquals(10.seconds, saved(id).position)
        assertTrue(output.isPlaying, "the output is still asked to play")

        advanceTimeBy(20.seconds)
        output.platformSuppress(false)
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(player.state.value.isPlaying)
        assertEquals(12.seconds, player.state.value.position)
    }

    @Test
    fun aLateChangeDoesNotUndoALaterPause() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        runCurrent()

        // Playing is reported only after the pause that followed it was queued.
        player.play()
        player.pause()
        output.signalChange()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
    }

    @Test
    fun anErrorStopsPlayback() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(2.seconds)

        output.platformFail()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(2.seconds, saved(id).position)
    }

    @Test
    fun reachingTheEndPausesAtTheDuration() = rule.testScope.runTest {
        val id = addSampleBook(position = 185.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()

        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(190.seconds, player.state.value.position)
        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(190.seconds, saved(id).position)
    }

    @Test
    fun playingAtTheEndStartsOver() = rule.testScope.runTest {
        val id = addSampleBook(position = 190.seconds)
        val output = output()
        val player = player(output)
        player.load(id)

        player.play()
        runCurrent()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(FilePosition(0, Duration.ZERO), output.seeks.last())
        assertEquals(2.seconds, player.state.value.position)
    }

    @Test
    fun savesWhenTheAppGoesToTheBackground() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player(output())
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(2_500.milliseconds)

        backgrounded.emit(Unit)
        runCurrent()

        assertEquals(2_500.milliseconds, saved(id).position)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun loadingAnotherBookPausesAndSavesTheFirst() = rule.testScope.runTest {
        val first = addSampleBook()
        val second = addSampleBook(title = "Other Book", position = 42.seconds)
        val output = output()
        val player = player(output)
        player.load(first)
        player.play()
        runCurrent()
        advanceTimeBy(3.seconds)

        player.load(second)
        runCurrent()

        assertEquals(3.seconds, saved(first).position)
        assertEquals("Other Book", player.state.value.book?.title)
        assertEquals(42.seconds, player.state.value.position)
        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals("Books/Other Book", output.files.first().ref.root)
    }

    @Test
    fun loadingThePlayingBookAgainKeepsPlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player(output())
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(2.seconds)

        player.load(id)
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(3.seconds, player.state.value.position)
    }

    @Test
    fun aBookWithoutPlayableFilesDoesNotPlay() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Empty", duration = 60.seconds))
        val output = output()
        val player = player(output)
        player.load(id)

        player.play()
        runCurrent()

        assertEquals("Empty", player.state.value.book?.title)
        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
    }

    // Availability (M14)

    @Test
    fun playingAnUnavailableBookStaysPausedAndPlaysNothing() = rule.testScope.runTest {
        val id = addSampleBook(position = 30.seconds)
        availability.set(id, Availability.Missing)
        val output = output()
        val player = player(output)
        player.load(id)

        player.play()
        runCurrent()
        advanceTimeBy(3.seconds)
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(30.seconds, player.state.value.position)
        assertEquals(listOf(id), availability.checks)
        assertEquals(Availability.Missing, availability.availability.value[id])
        assertNull(saved(id).lastPlayedAt)
    }

    @Test
    fun accessLostAlsoStaysPaused() = rule.testScope.runTest {
        val id = addSampleBook()
        availability.set(id, Availability.AccessLost)
        val output = output()
        val player = player(output)
        player.load(id)

        player.play()
        runCurrent()

        assertFalse(output.isPlaying)
    }

    @Test
    fun anAvailableBookIsCheckedAndPlays() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)

        player.play()
        runCurrent()

        assertTrue(output.isPlaying)
        assertEquals(listOf(id), availability.checks)
    }

    @Test
    fun aFileGoingMissingDuringPlaybackStopsWithTheBookUnavailable() = rule.testScope.runTest {
        val id = addSampleBook(position = 60.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(3.seconds)

        // The second file is deleted; the output fails when it gets to it.
        availability.set(id, Availability.Missing)
        output.platformFail()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(63.seconds, player.state.value.position)
        assertEquals(63.seconds, saved(id).position)
        assertEquals(Availability.Missing, availability.availability.value[id])
        assertEquals(listOf(id, id), availability.checks)
    }

    @Test
    fun aPlayingBookIsCheckedEveryThirtySecondsAndStopsWhenAFileWentMissing() = rule.testScope.runTest {
        // The platform may have buffered the rest of the book, so no error comes: the periodic check stops it.
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(listOf(id), availability.checks)

        availability.set(id, Availability.Missing)
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(listOf(id, id), availability.checks)
        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(30.seconds, saved(id).position)
        assertEquals(Availability.Missing, availability.availability.value[id])
    }

    @Test
    fun aPlayingBookThatIsStillThereKeepsPlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()

        advanceTimeBy(61.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(3, availability.checks.size)
    }

    @Test
    fun aFailureIsCheckedOnceNotOnEveryChange() = rule.testScope.runTest {
        val id = addSampleBook()
        val output = output()
        val player = player(output)
        player.load(id)
        player.play()
        runCurrent()

        output.platformFail()
        runCurrent()
        output.signalChange()
        runCurrent()
        output.signalChange()
        runCurrent()

        assertEquals(listOf(id, id), availability.checks)
    }

    @Test
    fun aPanelPlayOfAMissingBookEndsPausedAndUnavailable() = rule.testScope.runTest {
        // On Android the panel's play goes straight to the platform, which fails to open the missing file.
        val id = addSampleBook()
        availability.set(id, Availability.Missing)
        val output = output()
        val player = player(output)
        player.load(id)
        runCurrent()

        output.platformPlay()
        runCurrent()
        output.platformFail()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertFalse(output.isPlaying)
        assertEquals(Availability.Missing, availability.availability.value[id])
    }

    @Test
    fun playingAfterARelinkLoadsTheFilesFromTheirNewPlace() = rule.testScope.runTest {
        val id = addSampleBook(position = 90.seconds)
        val output = output()
        val player = player(output)
        player.load(id)
        runCurrent()
        // Relinked to `Books/Moved`, as the library would after the user picked it.
        val details = repository.all.single().let { it.copy(files = it.files.map { file -> file.copy(ref = file.ref.copy(root = "Books/Moved")) }) }
        repository.add(details.book, details.files, details.chapters)

        player.play()
        runCurrent()

        assertEquals(listOf("Books/Moved", "Books/Moved"), output.files.map { it.ref.root })
        assertTrue(output.isPlaying)
        assertEquals(FilePosition(1, 25.seconds), status(output).position)
        assertEquals(90.seconds, player.state.value.position)
    }

    @Test
    fun filesLeftOutByAPartialRelinkAreNotGivenToTheOutput() = rule.testScope.runTest {
        val id = repository.add(
            fakeBook("Gaps", duration = 180.seconds),
            files = listOf(
                file("Gaps", 0, "01.mp3", start = Duration.ZERO, duration = 60.seconds),
                file("Gaps", 1, "02.mp3", start = 60.seconds, duration = 60.seconds).copy(isMissing = true),
                file("Gaps", 2, "03.mp3", start = 120.seconds, duration = 60.seconds),
            ),
        )
        val output = output()
        val player = player(output)
        player.load(id)

        player.seekTo(90.seconds)
        runCurrent()

        assertEquals(listOf("01.mp3", "03.mp3"), output.files.map { it.ref.child })
        // Inside the left-out file: on from the start of the next one.
        assertEquals(FilePosition(1, Duration.ZERO), output.seeks.last())
        assertEquals(120.seconds, player.state.value.position)
        assertEquals(120.seconds, saved(id).position)
    }
}
