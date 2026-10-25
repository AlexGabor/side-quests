package com.alexgabor.bookplate.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.PlaybackState
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The sheets Player can show over itself. */
enum class PlayerSheet { Speed, Chapters }

/**
 * The book as Player shows it.
 *
 * @property position where the position track and label are: the scrubbed position while the user drags
 *   the track, the playback position otherwise.
 * @property chapterTitle the current chapter's title, or `null` when it has none.
 */
data class PlayerUi(
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapterTitle: String?,
    val position: Duration,
    val duration: Duration,
    val positionLabel: String,
    val durationLabel: String,
    val isPlaying: Boolean,
    val speed: Float,
    val speedLabel: String,
    val chapters: List<ChapterListItem>,
)

/** One line of the chapters sheet. */
sealed interface ChapterListItem {

    /** A section's title, heading the chapters after it. */
    data class Section(val title: String, val depth: Int) : ChapterListItem

    /** A playable chapter; [index] is what `jumpToChapter` takes. [depth] is how many sections hold it. */
    data class Entry(
        val index: Int,
        val title: String,
        val duration: String,
        val isCurrent: Boolean,
        val depth: Int,
    ) : ChapterListItem
}

/**
 * Player for book [bookId]: loads it into [player] (a no-op when it is already loaded) and shows it once
 * the player holds it. Commands go straight to [player], which outlives this holder.
 *
 * Only the open [sheet] is saved; everything else is read back from the player.
 */
class PlayerScreenState(
    private val scope: CoroutineScope,
    val bookId: BookId,
    private val player: BookPlayer,
    sheet: PlayerSheet? = null,
) {

    /** `null` until the player holds [bookId]. */
    val ui: State<PlayerUi?>
        field = mutableStateOf<PlayerUi?>(null)

    val sheet: State<PlayerSheet?>
        field = mutableStateOf(sheet)

    private var playback = PlaybackState()

    /** Where the user has dragged the track to, until the player has caught up with it. */
    private var scrubbing: Duration? = null
    private var scrubTimeout: Job? = null

    init {
        player.load(bookId)
        scope.launch {
            player.state.collect { state ->
                playback = state
                // Once a scrub has been let go, the player reporting its target ends it.
                if (scrubTimeout != null && scrubbing?.let { state.isNear(it) } == true) clearScrub()
                publish()
            }
        }
    }

    fun onPlayPause() {
        val state = playback.takeIf { it.book?.id == bookId } ?: return
        if (state.isPlaying) player.pause() else player.play()
    }

    fun onSkipBack() = player.skipBack()

    fun onSkipForward() = player.skipForward()

    /** The user is dragging the position track, which is at [position]. */
    fun onScrub(position: Duration) {
        scrubTimeout?.cancel()
        scrubbing = position.coerceIn(Duration.ZERO, playback.duration)
        publish()
    }

    /**
     * The track came to rest at [position]: seek there. The label stays at [position] until the player
     * reports it, so the track doesn't jump back to the old position in between.
     */
    fun onScrubEnd(position: Duration) {
        onScrub(position)
        player.seekTo(position)
        scrubTimeout = scope.launch {
            delay(ScrubHold)
            clearScrub()
            publish()
        }
    }

    fun onSpeedChange(speed: Float) = player.setSpeed(speed)

    fun onChapterClick(index: Int) = player.jumpToChapter(index)

    fun onSpeedClick() {
        sheet.value = PlayerSheet.Speed
    }

    fun onChaptersClick() {
        sheet.value = PlayerSheet.Chapters
    }

    fun onSheetDismissed() {
        sheet.value = null
    }

    private fun clearScrub() {
        scrubTimeout?.cancel()
        scrubTimeout = null
        scrubbing = null
    }

    private fun publish() {
        val book = playback.book?.takeIf { it.id == bookId }
        ui.value = book?.let { playback.toUi(scrubbing) }
    }

    private fun PlaybackState.isNear(target: Duration): Boolean =
        (position - target).absoluteValue < 2.seconds

    private companion object {
        /** How long a scrubbed position is held for a player that doesn't report it. */
        val ScrubHold = 1.seconds
    }
}

internal fun PlaybackState.toUi(scrubbing: Duration?): PlayerUi {
    val book = requireNotNull(book)
    val shown = scrubbing ?: position
    val current = chapterIndex
    return PlayerUi(
        title = book.title,
        author = book.author ?: "Unknown author",
        coverPath = book.coverPath,
        chapterTitle = chapter?.title,
        position = shown,
        duration = duration,
        positionLabel = formatDuration(shown),
        durationLabel = formatDuration(duration),
        isPlaying = isPlaying,
        speed = speed,
        speedLabel = formatSpeed(speed),
        chapters = chapterItems(book.chapters, current),
    )
}

/** Sections as headings, followed by their chapters; [current] indexes the playable chapters. */
internal fun chapterItems(chapters: List<Chapter>, current: Int?): List<ChapterListItem> {
    val items = mutableListOf<ChapterListItem>()
    var index = 0
    fun add(chapters: List<Chapter>, depth: Int) {
        for (chapter in chapters) {
            if (chapter.isSection) {
                items += ChapterListItem.Section(chapter.title ?: "Part", depth)
                add(chapter.children, depth + 1)
            } else {
                items += ChapterListItem.Entry(
                    index = index,
                    title = chapter.title ?: "Chapter ${index + 1}",
                    duration = formatDuration(chapter.end - chapter.start),
                    isCurrent = index == current,
                    depth = depth,
                )
                index++
            }
        }
    }
    add(chapters, depth = 0)
    return items
}

/** `m:ss` under an hour, `h:mm:ss` from an hour. */
internal fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "$hours:${minutes.pad()}:${seconds.pad()}" else "$minutes:${seconds.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')

/** `1.0×`, `0.3×`. */
internal fun formatSpeed(speed: Float): String {
    val tenths = (speed * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}×"
}

internal fun playerScreenStateSaver(
    scope: CoroutineScope,
    bookId: BookId,
    player: BookPlayer,
): Saver<PlayerScreenState, Int> = Saver(
    save = { state -> state.sheet.value?.ordinal ?: -1 },
    restore = { saved -> PlayerScreenState(scope, bookId, player, sheet = PlayerSheet.entries.getOrNull(saved)) },
)

@Composable
fun rememberPlayerScreenState(
    bookId: BookId,
    player: BookPlayer = koinInject(),
): PlayerScreenState {
    val scope = rememberCoroutineScope()
    val saver = remember(scope, bookId, player) { playerScreenStateSaver(scope, bookId, player) }
    return rememberSaveable(scope, bookId, player, saver = saver) {
        PlayerScreenState(scope, bookId, player)
    }
}
