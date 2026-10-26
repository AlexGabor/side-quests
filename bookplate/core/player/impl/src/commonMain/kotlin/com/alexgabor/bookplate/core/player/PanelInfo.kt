package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
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
)

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
