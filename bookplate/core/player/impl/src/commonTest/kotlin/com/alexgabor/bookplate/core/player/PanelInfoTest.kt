package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PanelInfoTest {

    private val sampleChapters = listOf(
        Chapter("Opening", 0.seconds, 65.seconds),
        Chapter("Disc 2", 65.seconds, 190.seconds, children = listOf(Chapter("Middle", 65.seconds, 190.seconds))),
    )

    private fun state(
        title: String = "Sample Book",
        author: String? = "Sample Author",
        chapters: List<Chapter> = sampleChapters,
        position: Int = 90,
        isPlaying: Boolean = true,
        speed: Float = 1f,
    ) = PlaybackState(
        book = PlayerBook(BookId(1), title, author, "/covers/1.jpg", 190.seconds, chapters),
        position = position.seconds,
        isPlaying = isPlaying,
        speed = speed,
    )

    @Test
    fun nothingLoadedShowsNothing() {
        assertNull(PlaybackState().panelInfo())
    }

    @Test
    fun showsTheBookWithItsChapterAsSubtitle() {
        val info = assertNotNull(state(speed = 1.5f).panelInfo())

        assertEquals("Sample Book", info.title)
        assertEquals("Middle", info.subtitle)
        assertEquals("/covers/1.jpg", info.coverPath)
        assertEquals(190.seconds, info.duration)
        assertEquals(90.seconds, info.position)
        assertEquals(true, info.isPlaying)
        assertEquals(1.5f, info.speed)
    }

    @Test
    fun subtitleFollowsTheChapter() {
        assertEquals("Opening", state(position = 64).panelInfo()?.subtitle)
        assertEquals("Middle", state(position = 65).panelInfo()?.subtitle)
    }

    @Test
    fun authorWhenTheBookHasNoChapters() {
        assertEquals("Sample Author", state(chapters = emptyList()).panelInfo()?.subtitle)
    }

    @Test
    fun authorWhenTheChapterIsUntitledOrTheBookItself() {
        assertEquals("Sample Author", state(chapters = listOf(Chapter(null, 0.seconds, 190.seconds))).panelInfo()?.subtitle)
        assertEquals("Sample Author", state(chapters = listOf(Chapter("  ", 0.seconds, 190.seconds))).panelInfo()?.subtitle)
        assertEquals(
            "Sample Author",
            state(title = "Single", chapters = listOf(Chapter("single", 0.seconds, 190.seconds))).panelInfo()?.subtitle,
        )
    }

    @Test
    fun noSubtitleWithoutChapterOrAuthor() {
        assertNull(state(author = null, chapters = emptyList()).panelInfo()?.subtitle)
        assertNull(state(author = " ", chapters = emptyList()).panelInfo()?.subtitle)
    }

    @Test
    fun updatesPushTheFirstStateAndThenOnlyChanges() {
        val time = TestTimeSource()
        val updates = PanelUpdates(time)

        assertTrue(updates.shouldPush(state(position = 10).panelInfo()))
        time += 1.seconds
        assertFalse(updates.shouldPush(state(position = 11).panelInfo()), "playing on as extrapolated")
        time += 1.seconds
        assertTrue(updates.shouldPush(state(position = 12, isPlaying = false).panelInfo()), "paused")
        time += 5.seconds
        assertFalse(updates.shouldPush(state(position = 12, isPlaying = false).panelInfo()), "still paused")
    }

    @Test
    fun updatesPushSeeksButNotSmallDrift() {
        val time = TestTimeSource()
        val updates = PanelUpdates(time)
        updates.shouldPush(state(position = 10, speed = 2f).panelInfo())

        time += 2.seconds
        assertFalse(updates.shouldPush(state(position = 14, speed = 2f).panelInfo()), "2 s at 2×")
        time += 1.seconds
        assertFalse(updates.shouldPush(state(position = 15, speed = 2f).panelInfo()), "1 s behind the guess, within tolerance")
        time += 1.seconds
        assertTrue(updates.shouldPush(state(position = 60, speed = 2f).panelInfo()), "a seek")
    }

    @Test
    fun updatesPushChapterSpeedAndUnloading() {
        val time = TestTimeSource()
        val updates = PanelUpdates(time)
        updates.shouldPush(state(position = 64).panelInfo())

        time += 1.seconds
        assertTrue(updates.shouldPush(state(position = 65).panelInfo()), "chapter changed")
        assertTrue(updates.shouldPush(state(position = 65, speed = 2f).panelInfo()))
        assertTrue(updates.shouldPush(PlaybackState().panelInfo()), "unloaded")
        assertFalse(updates.shouldPush(null), "nothing loaded is already pushed")
    }
}
