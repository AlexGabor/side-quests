package com.alexgabor.bookplate.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.alexgabor.bookplate.core.library.BookDetails
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.PlaybackState
import com.alexgabor.bookplate.core.player.PlayerBook
import com.alexgabor.bookplate.core.player.SkipInterval
import com.alexgabor.bookplate.core.player.snapSpeed
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
 * @property position where the position track is: the scrubbed position while the user drags it, the
 *   playback position otherwise.
 * @property positionLabel the playback position, also while the user drags the track.
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
 * Player for book [bookId]. When [player] holds it, Player shows and drives the player, which outlives this
 * holder. When the player is playing another book, that one keeps playing: Player shows [bookId] as
 * [library] has it, "Play" loads and plays it, and seeking, skipping, chapter jumps and speed change its
 * saved progress. Otherwise opening loads [bookId] (a no-op when it is already loaded).
 *
 * Only the open [sheet] is saved; everything else is read back from the player and the library.
 */
class PlayerScreenState(
    private val scope: CoroutineScope,
    val bookId: BookId,
    private val player: BookPlayer,
    private val library: LibraryRepository,
    sheet: PlayerSheet? = null,
) {

    /** `null` until the book is known, from the player or the library. */
    val ui: State<PlayerUi?>
        field = mutableStateOf<PlayerUi?>(null)

    val sheet: State<PlayerSheet?>
        field = mutableStateOf(sheet)

    private var playback = PlaybackState()

    /** [bookId] as the library holds it: what Player shows while the player holds another book. */
    private var saved: PlaybackState? = null

    /**
     * Whether commands go to the player: once it has been asked to load [bookId], even before it reports
     * holding it, so they queue up behind the load.
     */
    private var drivesPlayer = false

    private val shown: PlaybackState? get() = if (playback.book?.id == bookId) playback else saved

    /** Where the user has dragged the track to, until the player has caught up with it. */
    private var scrubbing: Duration? = null
    private var scrubTimeout: Job? = null

    init {
        val current = player.state.value
        val playsAnother = current.isPlaying && current.book?.id != bookId
        if (!playsAnother) load()
        scope.launch {
            player.state.collect { state ->
                playback = state
                onShownChanged()
            }
        }
        scope.launch {
            library.book(bookId).collect { details ->
                saved = details?.toSavedState()
                onShownChanged()
            }
        }
    }

    fun onPlayPause() {
        if (!drivesPlayer) {
            if (saved == null) return
            load()
        }
        if (shown?.isPlaying == true) player.pause() else player.play()
    }

    fun onSkipBack() = if (drivesPlayer) player.skipBack() else saveAt { it - SkipInterval }

    fun onSkipForward() = if (drivesPlayer) player.skipForward() else saveAt { it + SkipInterval }

    /** The user is dragging the position track, which is at [position]. */
    fun onScrub(position: Duration) {
        scrubTimeout?.cancel()
        scrubbing = position.coerceIn(Duration.ZERO, shown?.duration ?: Duration.ZERO)
        publish()
    }

    /**
     * The track came to rest at [position]: seek there. The track stays at [position] until the player
     * reports it, so it doesn't jump back to the old position in between.
     */
    fun onScrubEnd(position: Duration) {
        onScrub(position)
        if (drivesPlayer) player.seekTo(position) else saveAt { position }
        scrubTimeout = scope.launch {
            delay(ScrubHold)
            clearScrub()
            publish()
        }
    }

    fun onSpeedChange(speed: Float) {
        if (drivesPlayer) return player.setSpeed(speed)
        val book = saved ?: return
        val snapped = snapSpeed(speed)
        saved = book.copy(speed = snapped)
        publish()
        scope.launch { library.setSpeed(bookId, snapped) }
    }

    fun onChapterClick(index: Int) {
        if (drivesPlayer) return player.jumpToChapter(index)
        val chapter = saved?.book?.playableChapters?.getOrNull(index) ?: return
        saveAt { chapter.start }
    }

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

    /**
     * Moves the shown, not loaded book to [position] of where it is, within the book, and saves it. Shown at
     * once, so quick skips add up before the library reports back.
     */
    private fun saveAt(position: (Duration) -> Duration) {
        val book = saved ?: return
        val target = position(book.position).coerceIn(Duration.ZERO, book.duration)
        saved = book.copy(position = target)
        onShownChanged()
        scope.launch { library.setPosition(bookId, target) }
    }

    private fun load() {
        drivesPlayer = true
        player.load(bookId)
    }

    private fun onShownChanged() {
        // Once a scrub has been let go, the shown position reaching its target ends it.
        if (scrubTimeout != null && scrubbing?.let { shown?.isNear(it) } == true) clearScrub()
        publish()
    }

    private fun publish() {
        ui.value = shown?.takeIf { it.book != null }?.toUi(scrubbing)
    }

    private fun PlaybackState.isNear(target: Duration): Boolean =
        (position - target).absoluteValue < 2.seconds

    private companion object {
        /** How long a scrubbed position is held for a player that doesn't report it. */
        val ScrubHold = 1.seconds
    }
}

/** The book at its saved position and speed, paused, as the player would load it. */
private fun BookDetails.toSavedState(): PlaybackState = PlaybackState(
    book = PlayerBook(
        id = book.id,
        title = book.title,
        author = book.author,
        coverPath = book.coverPath,
        duration = book.duration,
        chapters = chapters,
    ),
    position = book.position.coerceIn(Duration.ZERO, book.duration),
    isPlaying = false,
    speed = snapSpeed(book.speed),
)

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
        positionLabel = formatDuration(position),
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
    library: LibraryRepository,
): Saver<PlayerScreenState, Int> = Saver(
    save = { state -> state.sheet.value?.ordinal ?: -1 },
    restore = { saved ->
        PlayerScreenState(scope, bookId, player, library, sheet = PlayerSheet.entries.getOrNull(saved))
    },
)

@Composable
fun rememberPlayerScreenState(
    bookId: BookId,
    player: BookPlayer = koinInject(),
    library: LibraryRepository = koinInject(),
): PlayerScreenState {
    val scope = rememberCoroutineScope()
    val saver = remember(scope, bookId, player, library) { playerScreenStateSaver(scope, bookId, player, library) }
    return rememberSaveable(scope, bookId, player, library, saver = saver) {
        PlayerScreenState(scope, bookId, player, library)
    }
}
