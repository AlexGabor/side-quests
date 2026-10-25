package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaybackStateTest {

    private fun book(chapters: List<Chapter>) =
        PlayerBook(BookId(1), "Book", author = null, coverPath = null, duration = 190.seconds, chapters = chapters)

    @Test
    fun nothingLoaded() {
        val state = PlaybackState()

        assertNull(state.book)
        assertEquals(Duration.ZERO, state.position)
        assertEquals(Duration.ZERO, state.duration)
        assertNull(state.chapterIndex)
        assertNull(state.chapter)
    }

    @Test
    fun currentChapterIsTheLastOneStartedAtThePosition() {
        val chapters = listOf(
            Chapter("One", 0.seconds, 65.seconds),
            Chapter("Two", 65.seconds, 190.seconds),
            Chapter("Three", 190.seconds, 200.seconds),
        )
        val state = PlaybackState(book(chapters), position = 2.minutes)

        assertEquals(1, state.chapterIndex)
        assertEquals("Two", state.chapter?.title)
        assertEquals(1, chapters.chapterIndexAt(65.seconds))
        assertEquals(2, chapters.chapterIndexAt(190.seconds))
    }

    @Test
    fun chaptersInsideSectionsAreIndexedAmongPlayableChapters() {
        val chapters = listOf(
            Chapter("Opening", 0.seconds, 65.seconds),
            Chapter("Disc 2", 65.seconds, 190.seconds, children = listOf(Chapter("Middle", 65.seconds, 190.seconds))),
        )
        val state = PlaybackState(book(chapters), position = 90.seconds)

        assertEquals(1, state.chapterIndex)
        assertEquals("Middle", state.chapter?.title)
    }

    @Test
    fun noChaptersMeansNoCurrentChapter() {
        assertNull(PlaybackState(book(emptyList()), position = 10.seconds).chapterIndex)
    }

    @Test
    fun speedIsSnappedToTenthsWithinRange() {
        assertEquals(1.3f, snapSpeed(1.26f))
        assertEquals(3f, snapSpeed(5f))
        assertEquals(0.3f, snapSpeed(0.1f))
        assertEquals(1f, snapSpeed(Float.NaN))
    }
}
