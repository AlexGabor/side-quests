package com.alexgabor.bookplate.feature.home

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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MiniPlayerStateTest {

    private val rule = TestScopeRule()
    private val repository = FakeLibraryRepository()

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    private fun TestScope.player() = FakeBookPlayer(repository, backgroundScope)

    private fun TestScope.mini(player: FakeBookPlayer) =
        MiniPlayerState(backgroundScope, repository, player).also { runCurrent() }

    private val MiniPlayerState.shown: MiniPlayerUi get() = assertNotNull(mini.value)

    @Test
    fun hiddenWhenNothingWasPlayed() = rule.testScope.runTest {
        repository.add(fakeBook("Dune"))

        assertNull(mini(player()).mini.value)
    }

    @Test
    fun showsTheLastPlayedBookAsSaved() = rule.testScope.runTest {
        val id = repository.add(
            fakeBook("Dune", duration = 2.minutes, position = 30.seconds, lastPlayedAt = Instant.fromEpochSeconds(10)),
        )
        repository.add(fakeBook("Emma", lastPlayedAt = Instant.fromEpochSeconds(5)))

        val shown = mini(player()).shown

        assertEquals(MiniPlayerUi(id, "Dune", progress = 0.25f, isPlaying = false), shown)
    }

    @Test
    fun appearsOncePlayingStartsAndFollowsThePlayer() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Dune", duration = 100.seconds))
        val player = player()
        val state = mini(player)
        assertNull(state.mini.value)

        player.load(id)
        player.play()
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals("Dune", state.shown.title)
        assertTrue(state.shown.isPlaying)
        assertEquals(0.1f, state.shown.progress)
    }

    @Test
    fun playLoadsTheBookFirst() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Dune", position = 30.seconds, lastPlayedAt = Instant.fromEpochSeconds(10)))
        val player = player()
        val state = mini(player)

        state.onPlayPause()
        runCurrent()

        assertEquals(id, player.state.value.book?.id)
        assertEquals(30.seconds, player.state.value.position)
        assertTrue(player.state.value.isPlaying)
        assertTrue(state.shown.isPlaying)
    }

    @Test
    fun playReplacesAnotherLoadedBook() = rule.testScope.runTest {
        val last = repository.add(fakeBook("Dune", lastPlayedAt = Instant.fromEpochSeconds(10)))
        val other = repository.add(fakeBook("Emma"))
        val player = player()
        player.load(other)
        val state = mini(player)

        state.onPlayPause()
        runCurrent()

        assertEquals(last, player.state.value.book?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun pausesThePlayingBook() = rule.testScope.runTest {
        val id = repository.add(fakeBook("Dune"))
        val player = player()
        player.load(id)
        player.play()
        val state = mini(player)

        state.onPlayPause()
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertFalse(state.shown.isPlaying)
    }
}
