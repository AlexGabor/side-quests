package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.lib.coroutine.TestScopeRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FakeBookPlayerTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()
    private val now = Instant.fromEpochSeconds(1_000_000)
    private val clock = object : Clock {
        override fun now() = now
    }

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

    private fun TestScope.player() = FakeBookPlayer(repository, backgroundScope, clock)

    private fun saved(id: BookId) = repository.all.first { it.book.id == id }.book

    @Test
    fun loadsABookPausedAtItsSavedPositionAndSpeed() = rule.testScope.runTest {
        val id = addSampleBook(position = 42.seconds, speed = 1.5f)
        val player = player()

        player.load(id)
        runCurrent()

        val state = player.state.value
        assertEquals("Sample Book", state.book?.title)
        assertEquals(42.seconds, state.position)
        assertEquals(1.5f, state.speed)
        assertFalse(state.isPlaying)
        assertEquals(190.seconds, state.duration)
    }

    @Test
    fun unknownBookLeavesNothingLoaded() = rule.testScope.runTest {
        val player = player()

        player.load(BookId(99))
        runCurrent()

        assertNull(player.state.value.book)
    }

    @Test
    fun playsAtItsSpeed() = rule.testScope.runTest {
        val id = addSampleBook(position = 10.seconds, speed = 1.5f)
        val player = player()

        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(25.seconds, player.state.value.position)
    }

    @Test
    fun stopsPausedAtTheEnd() = rule.testScope.runTest {
        val id = addSampleBook(position = 3.minutes)
        val player = player()

        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(190.seconds, player.state.value.position)
        assertFalse(player.state.value.isPlaying)
        assertEquals(190.seconds, saved(id).position)
    }

    @Test
    fun playingAtTheEndStartsOver() = rule.testScope.runTest {
        val id = addSampleBook(position = 190.seconds)
        val player = player()

        player.load(id)
        player.play()
        runCurrent()

        assertEquals(Duration.ZERO, player.state.value.position)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun loadingTheLoadedBookChangesNothing() = rule.testScope.runTest {
        val id = addSampleBook(position = 1.minutes)
        val player = player()
        player.load(id)
        player.play()
        runCurrent()

        player.load(id)
        runCurrent()

        assertTrue(player.state.value.isPlaying)
        assertEquals(1.minutes, player.state.value.position)
    }

    @Test
    fun loadingAnotherBookPausesAndSavesTheFirst() = rule.testScope.runTest {
        val first = addSampleBook()
        val second = repository.add(fakeBook("Other", position = 30.seconds))
        val player = player()
        player.load(first)
        player.play()
        runCurrent()
        advanceTimeBy(3.seconds)
        runCurrent()

        player.load(second)
        runCurrent()

        assertEquals(3.seconds, saved(first).position)
        assertEquals("Other", player.state.value.book?.title)
        assertEquals(30.seconds, player.state.value.position)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun skipsAreClampedToTheBook() = rule.testScope.runTest {
        val id = addSampleBook(position = 4.seconds)
        val player = player()
        player.load(id)

        player.skipBack()
        runCurrent()
        assertEquals(Duration.ZERO, player.state.value.position)

        player.seekTo(1.minutes)
        player.skipForward()
        runCurrent()
        assertEquals(70.seconds, player.state.value.position)

        player.seekTo(185.seconds)
        player.skipForward()
        runCurrent()
        assertEquals(190.seconds, player.state.value.position)
        assertEquals(190.seconds, saved(id).position)
    }

    @Test
    fun seekIsClamped() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        player.load(id)

        player.seekTo(10.minutes)
        runCurrent()

        assertEquals(190.seconds, player.state.value.position)
    }

    @Test
    fun jumpsToAChapterAndKeepsPlaying() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        player.load(id)
        player.play()

        player.jumpToChapter(1)
        player.jumpToChapter(5)
        runCurrent()

        assertEquals(65.seconds, player.state.value.position)
        assertEquals(1, player.state.value.chapterIndex)
        assertEquals("Middle", player.state.value.chapter?.title)
        assertTrue(player.state.value.isPlaying)
        assertEquals(65.seconds, saved(id).position)
    }

    @Test
    fun speedIsSnappedAndSavedPerBook() = rule.testScope.runTest {
        val a = addSampleBook()
        val b = repository.add(fakeBook("Other", speed = 1f))
        val player = player()

        player.load(a)
        player.setSpeed(1.26f)
        runCurrent()
        assertEquals(1.3f, player.state.value.speed)

        player.setSpeed(5f)
        runCurrent()
        assertEquals(3f, player.state.value.speed)
        assertEquals(3f, saved(a).speed)

        player.load(b)
        runCurrent()
        assertEquals(1f, player.state.value.speed)

        player.load(a)
        runCurrent()
        assertEquals(3f, player.state.value.speed)
    }

    @Test
    fun pauseSavesPositionAndLastPlayed() = rule.testScope.runTest {
        val id = addSampleBook()
        val player = player()
        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(3.seconds)
        runCurrent()

        player.pause()
        runCurrent()
        advanceTimeBy(3.seconds)
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals(3.seconds, player.state.value.position)
        assertEquals(3.seconds, saved(id).position)
        assertEquals(now, saved(id).lastPlayedAt)
    }

    @Test
    fun playingSavesPeriodicallyAndBecomesLastPlayed() = rule.testScope.runTest {
        val id = addSampleBook()
        repository.add(fakeBook("Other"))
        val player = player()
        player.load(id)
        player.play()
        runCurrent()

        assertEquals(id, repository.lastPlayed.first()?.id)

        advanceTimeBy(6.seconds)
        runCurrent()
        assertTrue(saved(id).position >= 5.seconds)
    }

    @Test
    fun playWithNothingLoadedDoesNothing() = rule.testScope.runTest {
        val player = player()

        player.play()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
    }
}
