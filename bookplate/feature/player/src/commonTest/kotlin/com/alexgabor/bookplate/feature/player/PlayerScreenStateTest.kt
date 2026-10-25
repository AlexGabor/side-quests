package com.alexgabor.bookplate.feature.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.bookplate.core.player.FakeBookPlayer
import com.alexgabor.lib.coroutine.TestScopeRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class PlayerScreenStateTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    /** `Sample Book`: `Opening` 0:00–1:05, then section `Disc 2` holding `Middle` 1:05–3:10. */
    private fun addSampleBook(position: Duration = Duration.ZERO, speed: Float = 1f): BookId = repository.add(
        fakeBook("Sample Book", author = "Sample Author", duration = 190.seconds, position = position, speed = speed),
        chapters = listOf(
            Chapter("Opening", 0.seconds, 65.seconds),
            Chapter("Disc 2", 65.seconds, 190.seconds, children = listOf(Chapter("Middle", 65.seconds, 190.seconds))),
        ),
    )

    private fun TestScope.player() = FakeBookPlayer(repository, backgroundScope)

    private fun TestScope.screen(id: BookId, player: FakeBookPlayer, sheet: PlayerSheet? = null) =
        PlayerScreenState(backgroundScope, id, player, repository, sheet).also { runCurrent() }

    private val PlayerScreenState.shown: PlayerUi get() = assertNotNull(ui.value)

    @Test
    fun showsTheBookOnceLoaded() = rule.testScope.runTest {
        val id = addSampleBook(position = 90.seconds)

        val ui = screen(id, player()).shown

        assertEquals("Sample Book", ui.title)
        assertEquals("Sample Author", ui.author)
        assertEquals("Middle", ui.chapterTitle)
        assertEquals("1:30", ui.positionLabel)
        assertEquals("3:10", ui.durationLabel)
        assertFalse(ui.isPlaying)
        assertEquals("1.0×", ui.speedLabel)
    }

    @Test
    fun noAuthorAndLongDurations() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Long", duration = 2.hours, position = 1.hours + 5.minutes + 3.seconds))

        val ui = screen(id, player()).shown

        assertEquals("Unknown author", ui.author)
        assertEquals("1:05:03", ui.positionLabel)
        assertEquals("2:00:00", ui.durationLabel)
        assertNull(ui.chapterTitle)
    }

    @Test
    fun showsNothingWhileThePlayerHoldsAnotherBook() = rule.testScope.runTest {
        val id = addSampleBook()

        val state = PlayerScreenState(backgroundScope, BookId(99), player(), repository)
        runCurrent()

        assertNull(state.ui.value)
        assertTrue(repository.all.any { it.book.id == id })
    }

    @Test
    fun openingTheLoadedBookKeepsItPlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        player.load(id)
        player.play()
        runCurrent()

        val state = screen(id, player)

        assertTrue(state.shown.isPlaying)
    }

    private data class Opened(val first: BookId, val second: BookId, val player: FakeBookPlayer, val state: PlayerScreenState)

    /** `Sample Book` playing, and Player open for "Other", saved at 0:30 at 1.5×. */
    private fun TestScope.openOtherWhileFirstPlays(): Opened {
        val first = addSampleBook()
        val second = repository.add(
            fakeBook("Other", duration = 190.seconds, position = 30.seconds, speed = 1.5f),
            chapters = listOf(Chapter("One", 0.seconds, 100.seconds), Chapter("Two", 100.seconds, 190.seconds)),
        )
        val player = player()
        player.load(first)
        player.play()
        runCurrent()
        return Opened(first, second, player, screen(second, player))
    }

    private fun saved(id: BookId) = repository.all.first { it.book.id == id }.book

    @Test
    fun openingAnotherBookKeepsTheFirstPlaying() = rule.testScope.runTest {
        val (first, _, player, state) = openOtherWhileFirstPlays()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals("Other", state.shown.title)
        assertEquals("0:30", state.shown.positionLabel)
        assertEquals("1.5×", state.shown.speedLabel)
        assertEquals("One", state.shown.chapterTitle)
        assertFalse(state.shown.isPlaying)
        assertEquals(first, player.state.value.book?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun playOnTheOtherBookTakesOver() = rule.testScope.runTest {
        val (first, second, player, state) = openOtherWhileFirstPlays()
        advanceTimeBy(3.seconds)
        runCurrent()

        state.onPlayPause()
        runCurrent()

        assertEquals(second, player.state.value.book?.id)
        assertTrue(player.state.value.isPlaying)
        assertTrue(state.shown.isPlaying)
        assertEquals("0:30", state.shown.positionLabel)
        assertEquals(3.seconds, saved(first).position)

        state.onPlayPause()
        runCurrent()
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun skippingTheOtherBookSavesItAndKeepsTheFirstPlaying() = rule.testScope.runTest {
        val (first, second, player, state) = openOtherWhileFirstPlays()

        state.onSkipForward()
        state.onSkipForward()
        runCurrent()
        assertEquals("0:50", state.shown.positionLabel)
        assertEquals(50.seconds, saved(second).position)

        state.onSkipBack()
        runCurrent()
        assertEquals("0:40", state.shown.positionLabel)
        assertEquals(40.seconds, saved(second).position)

        assertEquals(first, player.state.value.book?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun seekJumpAndSpeedOnTheOtherBookAreSaved() = rule.testScope.runTest {
        val (first, second, player, state) = openOtherWhileFirstPlays()

        state.onScrubEnd(500.seconds)
        runCurrent()
        assertEquals("3:10", state.shown.positionLabel)
        assertEquals(190.seconds, saved(second).position)

        state.onChapterClick(1)
        runCurrent()
        assertEquals("1:40", state.shown.positionLabel)
        assertEquals("Two", state.shown.chapterTitle)
        assertEquals(100.seconds, saved(second).position)

        state.onSpeedChange(2.04f)
        runCurrent()
        assertEquals("2.0×", state.shown.speedLabel)
        assertEquals(2f, saved(second).speed)

        assertEquals(first, player.state.value.book?.id)
        assertTrue(player.state.value.isPlaying)
        assertEquals(1f, player.state.value.speed)
    }

    @Test
    fun openingAnotherBookWhileNothingPlaysLoadsIt() = rule.testScope.runTest {
        val first = addSampleBook()
        val second = repository.add(fakeBook("Other", position = 30.seconds))
        val player = player()
        player.load(first)
        runCurrent()

        val state = screen(second, player)
        state.onSkipForward()
        runCurrent()

        assertEquals(second, player.state.value.book?.id)
        assertEquals("0:40", state.shown.positionLabel)
        assertFalse(state.shown.isPlaying)
    }

    @Test
    fun playAndPause() = rule.testScope.runTest {
        val state = screen(addSampleBook(), player())

        state.onPlayPause()
        runCurrent()
        assertTrue(state.shown.isPlaying)

        advanceTimeBy(2.seconds)
        runCurrent()
        state.onPlayPause()
        runCurrent()
        assertFalse(state.shown.isPlaying)
        assertEquals("0:02", state.shown.positionLabel)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals("0:02", state.shown.positionLabel)
    }

    @Test
    fun skipsBothWays() = rule.testScope.runTest {
        val state = screen(addSampleBook(position = 1.minutes), player())

        state.onSkipForward()
        runCurrent()
        assertEquals("1:10", state.shown.positionLabel)

        state.onSkipBack()
        state.onSkipBack()
        runCurrent()
        assertEquals("0:50", state.shown.positionLabel)
    }

    @Test
    fun scrubbingShowsTheTrackPositionThenSeeks() = rule.testScope.runTest {
        val player = player()
        val state = screen(addSampleBook(), player)

        state.onScrub(2.minutes)
        assertEquals("2:00", state.shown.positionLabel)
        assertEquals(Duration.ZERO, player.state.value.position)

        state.onScrubEnd(2.minutes)
        runCurrent()
        assertEquals(2.minutes, player.state.value.position)
        assertEquals("2:00", state.shown.positionLabel)
        assertEquals("Middle", state.shown.chapterTitle)
    }

    @Test
    fun scrubbedLabelHoldsUntilThePlayerCatchesUp() = rule.testScope.runTest {
        val player = player()
        val state = screen(addSampleBook(), player)
        player.play()
        runCurrent()

        state.onScrub(150.seconds)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals("2:30", state.shown.positionLabel)

        state.onScrubEnd(150.seconds)
        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals("2:33", state.shown.positionLabel)
    }

    @Test
    fun speedIsSetAndShown() = rule.testScope.runTest {
        val id = addSampleBook()
        val state = screen(id, player())

        state.onSpeedChange(1.5f)
        runCurrent()

        assertEquals("1.5×", state.shown.speedLabel)
        assertEquals(1.5f, repository.all.first { it.book.id == id }.book.speed)
    }

    @Test
    fun speedIsKeptForTheBook() = rule.testScope.runTest {
        val id = addSampleBook(speed = 1.5f)

        assertEquals("1.5×", screen(id, player()).shown.speedLabel)
    }

    @Test
    fun chaptersListSectionsDurationsAndTheCurrentOne() = rule.testScope.runTest {
        val state = screen(addSampleBook(position = 10.seconds), player())

        assertEquals(
            listOf(
                ChapterListItem.Entry(0, "Opening", "1:05", isCurrent = true, depth = 0),
                ChapterListItem.Section("Disc 2", depth = 0),
                ChapterListItem.Entry(1, "Middle", "2:05", isCurrent = false, depth = 1),
            ),
            state.shown.chapters,
        )
    }

    @Test
    fun untitledChaptersAreNumbered() {
        val items = chapterItems(listOf(Chapter(null, 0.seconds, 10.seconds), Chapter(null, 10.seconds, 20.seconds)), current = 1)

        assertEquals(
            listOf(
                ChapterListItem.Entry(0, "Chapter 1", "0:10", isCurrent = false, depth = 0),
                ChapterListItem.Entry(1, "Chapter 2", "0:10", isCurrent = true, depth = 0),
            ),
            items,
        )
    }

    @Test
    fun jumpingToAChapter() = rule.testScope.runTest {
        val state = screen(addSampleBook(), player())

        state.onChapterClick(1)
        runCurrent()

        assertEquals("1:05", state.shown.positionLabel)
        assertEquals("Middle", state.shown.chapterTitle)
        assertTrue((state.shown.chapters[2] as ChapterListItem.Entry).isCurrent)
    }

    @Test
    fun sheetsOpenAndCloseAndAreRestored() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        val state = screen(id, player)

        state.onSpeedClick()
        assertEquals(PlayerSheet.Speed, state.sheet.value)
        state.onChaptersClick()
        assertEquals(PlayerSheet.Chapters, state.sheet.value)
        state.onSheetDismissed()
        assertNull(state.sheet.value)

        assertEquals(PlayerSheet.Speed, screen(id, player, sheet = PlayerSheet.Speed).sheet.value)
    }

    @Test
    fun speedLabels() {
        assertEquals("0.3×", formatSpeed(0.3f))
        assertEquals("3.0×", formatSpeed(3f))
        assertEquals("1.2×", formatSpeed(1.2f))
    }
}
