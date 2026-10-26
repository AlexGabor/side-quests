package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookInks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What the platform's media panel (Android's notification and lock screen, iOS's Now Playing) shows for the
 * loaded book. Positions are book-wide, like Player's: the panel's seek bar covers the whole book.
 *
 * @property subtitle the current chapter's title, or the author when there is no chapter title worth showing.
 * @property coverPath the cover copied into app storage, or `null` when the book has none.
 * @property inks the book's inks, which the artwork of a book without a cover is printed in.
 */
data class PanelInfo(
    val bookId: BookId,
    val title: String,
    val subtitle: String?,
    val coverPath: String?,
    val duration: Duration,
    val position: Duration,
    val isPlaying: Boolean,
    val speed: Float,
    val inks: BookInks = BookInks.Default,
) {
    /** The letter a coverless book's artwork shows: the title's first, upper-cased. */
    val artworkLetter: String get() = title.trim().take(1).uppercase()
}

/** A book's inks as colors, ARGB, for drawing the panel artwork of a book without a cover. */
data class PanelInks(val paper: Int, val content: Int, val accent: Int, val accentVariant: Int)

/** Turns a book's ink names into the colors they print in. `sharedApp` binds it from Riso. */
fun interface PanelInkColors {
    fun of(inks: BookInks): PanelInks
}

/**
 * The square artwork a book without a cover shows in the media panel, like Home's placeholder, as fractions of its
 * side: paper washed with the accent variant at [WASH_ALPHA], a border in the accent, and the title's letter in the
 * content ink. Each platform draws it with its own 2D API.
 */
object PanelArtwork {
    const val SIZE_PX = 512
    const val WASH_ALPHA = 0.4f
    const val BORDER_INSET = 0.06f
    const val BORDER_WIDTH = 0.025f
    const val CORNER_RADIUS = 0.12f
    const val LETTER_SIZE = 0.5f
}

/**
 * The panel's view of this state, or `null` with no book loaded.
 *
 * The subtitle is the current chapter's title, unless it is blank or just the book's title again (a single-file
 * book's one chapter usually is); then it is the author, if known.
 */
fun PlaybackState.panelInfo(): PanelInfo? {
    val book = book ?: return null
    val chapterTitle = chapter?.title?.trim()?.takeIf { it.isNotEmpty() && !it.equals(book.title.trim(), ignoreCase = true) }
    return PanelInfo(
        bookId = book.id,
        title = book.title,
        subtitle = chapterTitle ?: book.author?.takeIf { it.isNotBlank() },
        coverPath = book.coverPath,
        duration = book.duration,
        position = position,
        isPlaying = isPlaying,
        speed = speed,
        inks = book.inks,
    )
}

/**
 * Decides when a panel that runs its own clock must be told again. Such a panel moves the elapsed time on from
 * the last position and rate it was given, so the position only needs sending again when that guess is off by
 * more than [Tolerance]: after a seek, a skip, a chapter jump or a stall. Anything else that changed is always
 * sent.
 */
class PanelUpdates(private val timeSource: TimeSource = TimeSource.Monotonic) {

    private var last: PanelInfo? = null
    private var lastAt: TimeMark? = null
    private var started = false

    /**
     * Whether the panel must be given [info] (`null`: clear it), rather than going on with what it was last
     * given. Returning `true` takes [info] as given now.
     */
    fun shouldPush(info: PanelInfo?): Boolean {
        val previous = last
        val changed = when {
            !started -> true
            info == null || previous == null -> info != previous
            info.copy(position = previous.position) != previous -> true
            else -> (info.position - expected(previous)).absoluteValue > Tolerance
        }
        if (!changed) return false
        started = true
        last = info
        lastAt = timeSource.markNow()
        return true
    }

    /** Where the panel thinks [previous] has got to by now. */
    private fun expected(previous: PanelInfo): Duration {
        if (!previous.isPlaying) return previous.position
        val elapsed = lastAt?.elapsedNow() ?: Duration.ZERO
        return (previous.position + elapsed * previous.speed.toDouble()).coerceAtMost(previous.duration)
    }

    companion object {
        /** How far the panel's own clock may be off before the position is sent again. */
        val Tolerance: Duration = 1.seconds
    }
}
