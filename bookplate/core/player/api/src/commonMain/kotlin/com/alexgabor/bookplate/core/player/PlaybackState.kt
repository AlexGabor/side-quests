package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.playable
import kotlin.time.Duration

/**
 * What the player is doing, as one snapshot.
 *
 * @property book the loaded book, or `null` when none is.
 * @property position where playback is in [book]'s timeline, from zero to [duration].
 */
data class PlaybackState(
    val book: PlayerBook? = null,
    val position: Duration = Duration.ZERO,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
) {
    /** The loaded book's total duration; zero with no book. */
    val duration: Duration get() = book?.duration ?: Duration.ZERO

    /** Index in [PlayerBook.playableChapters] of the chapter at [position], or `null` when there are none. */
    val chapterIndex: Int? get() = book?.playableChapters?.chapterIndexAt(position)

    /** The chapter at [position], or `null` when the book has none. */
    val chapter: Chapter? get() = chapterIndex?.let { book?.playableChapters?.get(it) }
}

/**
 * The parts of a library book the player and its UI need.
 *
 * @property chapters top-level chapters, sections holding their own, as the library stores them.
 * @property inks the inks the book is printed in, followed while it is loaded.
 */
data class PlayerBook(
    val id: BookId,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val duration: Duration,
    val chapters: List<Chapter>,
    val inks: BookInks = BookInks.Default,
) {
    /** Every chapter that can be played, in order: what [PlaybackState.chapterIndex] indexes. */
    val playableChapters: List<Chapter> = chapters.playable()
}

/**
 * The last chapter starting at or before [position]; the first one when [position] is before every
 * chapter. `null` for no chapters.
 */
fun List<Chapter>.chapterIndexAt(position: Duration): Int? {
    if (isEmpty()) return null
    val after = indexOfFirst { it.start > position }
    return when (after) {
        -1 -> lastIndex
        0 -> 0
        else -> after - 1
    }
}
