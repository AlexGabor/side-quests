package com.alexgabor.bookplate.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.BookAvailability
import com.alexgabor.bookplate.core.library.BookDetails
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookInkPicker
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.bookplate.core.library.BookRelinker
import com.alexgabor.bookplate.core.library.Chapter
import com.alexgabor.bookplate.core.library.FailureReason
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.bookplate.core.library.RelinkResult
import com.alexgabor.bookplate.core.library.of
import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.PlaybackState
import com.alexgabor.bookplate.core.player.PlayerBook
import com.alexgabor.bookplate.core.player.SkipInterval
import com.alexgabor.bookplate.core.player.snapSpeed
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 * @property unavailable why the book's files can't be reached, shown in place of the controls; `null` while they can.
 * @property needsFolder the book was added from a folder, so relinking picks a folder; otherwise a file.
 * @property inks the inks the book is printed in.
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
    val unavailable: UnavailableUi? = null,
    val needsFolder: Boolean = true,
    val inks: BookInks = BookInks.Default,
)

/** What Player says about a book whose files can't be reached. */
data class UnavailableUi(val title: String, val message: String)

/** The relink Player is running, or the result of the last one. */
sealed interface RelinkStatus {
    val message: String

    data class Running(override val message: String) : RelinkStatus

    /** Only some files were found: [pick] is relinked once the user confirms [missing]. */
    data class NeedsConfirmation(
        override val message: String,
        val pick: PickedDocument,
        val missing: Set<String>,
    ) : RelinkStatus

    data class Finished(override val message: String, val isError: Boolean) : RelinkStatus
}

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
 * Opening checks the book with [availability]. While its files can't be reached, Player shows why instead of the
 * controls, and nothing plays from it; [onRelinkPicked] then points it at their new place through [relinker], asking
 * first when only some are found.
 *
 * The swatch button offers [candidateInks], from [inks]; [onInksClick] gives them to the book and picks the next.
 *
 * Only the open [sheet] and the candidate inks are saved; everything else is read back from the player and the
 * library.
 */
class PlayerScreenState(
    private val scope: CoroutineScope,
    val bookId: BookId,
    private val player: BookPlayer,
    private val library: LibraryRepository,
    private val availability: BookAvailability,
    private val relinker: BookRelinker,
    private val inks: BookInkPicker,
    sheet: PlayerSheet? = null,
    candidateInks: BookInks? = null,
) {

    /** `null` until the book is known, from the player or the library. */
    val ui: State<PlayerUi?>
        field = mutableStateOf<PlayerUi?>(null)

    val sheet: State<PlayerSheet?>
        field = mutableStateOf(sheet)

    /** The inks the swatch button shows: what the book gets when it is tapped. */
    val candidateInks: State<BookInks>
        field = mutableStateOf(candidateInks ?: inks.pick())

    /** The running relink, or the last one's result until it is closed; `null` for none. */
    val relink: State<RelinkStatus?>
        field = mutableStateOf<RelinkStatus?>(null)

    private var playback = PlaybackState()

    private var bookAvailability = Availability.Available
    private var sourceIsFolder = true
    private var bookInks = BookInks.Default

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
                details?.let {
                    sourceIsFolder = it.book.source.isFolder
                    bookInks = it.book.inks
                }
                onShownChanged()
            }
        }
        scope.launch {
            availability.availability.collect { checked ->
                bookAvailability = checked.of(bookId)
                publish()
            }
        }
        scope.launch { availability.check(bookId) }
    }

    private val isUnavailable: Boolean get() = bookAvailability != Availability.Available

    fun onPlayPause() {
        if (isUnavailable) return
        if (!drivesPlayer) {
            if (saved == null) return
            load()
        }
        if (shown?.isPlaying == true) player.pause() else player.play()
    }

    fun onSkipBack() {
        if (isUnavailable) return
        if (drivesPlayer) player.skipBack() else saveAt { it - SkipInterval }
    }

    fun onSkipForward() {
        if (isUnavailable) return
        if (drivesPlayer) player.skipForward() else saveAt { it + SkipInterval }
    }

    /** Checks the book's files again, as when they were put back. */
    fun onCheckAgain() {
        scope.launch { availability.check(bookId) }
    }

    /** The user picked [pick] for "Relink"; `null` is a cancelled pick. Ignored while a relink runs. */
    fun onRelinkPicked(pick: PickedDocument?) {
        if (pick == null) return
        runRelink(pick, confirmMissing = emptySet())
    }

    /** "Relink anyway" for a partial match: relinks again, accepting the files that weren't found. */
    fun onRelinkConfirmed() {
        val asked = relink.value as? RelinkStatus.NeedsConfirmation ?: return
        runRelink(asked.pick, asked.missing)
    }

    /** "Cancel" for a partial match: nothing changes. */
    fun onRelinkCancelled() {
        if (relink.value is RelinkStatus.NeedsConfirmation) relink.value = null
    }

    /** Closes a finished relink's result. */
    fun onRelinkStatusClosed() {
        if (relink.value is RelinkStatus.Finished) relink.value = null
    }

    private fun runRelink(pick: PickedDocument, confirmMissing: Set<String>) {
        if (relink.value is RelinkStatus.Running) return
        relink.value = RelinkStatus.Running("Looking for the book's files in “${pick.name}”…")
        scope.launch {
            val result = try {
                relinker.relink(bookId, pick, confirmMissing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RelinkResult.Failed(FailureReason.Unreadable, e.message)
            }
            relink.value = statusOf(result, pick)
        }
    }

    private suspend fun statusOf(result: RelinkResult, pick: PickedDocument): RelinkStatus? {
        val title = shown?.book?.title ?: pick.name
        return when (result) {
            is RelinkResult.Relinked -> {
                val skipped = result.leftOut.size
                val message = when (skipped) {
                    0 -> "Relinked “$title”"
                    1 -> "Relinked “$title”. 1 missing file will be skipped."
                    else -> "Relinked “$title”. $skipped missing files will be skipped."
                }
                RelinkStatus.Finished(message, isError = false)
            }
            RelinkResult.Unchanged -> RelinkStatus.Finished("“$title” is already linked to “${pick.name}”", isError = false)
            is RelinkResult.NeedsConfirmation -> RelinkStatus.NeedsConfirmation(
                message = "Found ${result.found} of ${result.total} files in “${pick.name}”. " +
                    "Not found: ${result.missing.joinToString(", ")}. Parts of the book without their files will be skipped.",
                pick = pick,
                missing = result.missing.toSet(),
            )
            RelinkResult.NoMatch -> RelinkStatus.Finished("None of this book's files are in “${pick.name}”", isError = true)
            is RelinkResult.WrongKind -> RelinkStatus.Finished(
                if (result.needsFolder) "“$title” was added from a folder. Pick its folder."
                else "“$title” was added from a single file. Pick the file.",
                isError = true,
            )
            is RelinkResult.OtherBook -> {
                val other = library.book(result.bookId).first()?.book?.title ?: pick.name
                RelinkStatus.Finished("“${pick.name}” is already the book “$other”", isError = true)
            }
            is RelinkResult.Failed ->
                RelinkStatus.Finished("Couldn't open “${pick.name}”. ${result.reason.explanation}", isError = true)
            RelinkResult.NoSuchBook -> null
        }
    }

    /** The user is dragging the position track, which is at [position]. */
    fun onScrub(position: Duration) {
        if (isUnavailable) return
        scrubTimeout?.cancel()
        scrubbing = position.coerceIn(Duration.ZERO, shown?.duration ?: Duration.ZERO)
        publish()
    }

    /**
     * The track came to rest at [position]: seek there. The track stays at [position] until the player
     * reports it, so it doesn't jump back to the old position in between.
     */
    fun onScrubEnd(position: Duration) {
        if (isUnavailable) return
        onScrub(position)
        if (drivesPlayer) player.seekTo(position) else saveAt { position }
        scrubTimeout = scope.launch {
            delay(ScrubHold)
            clearScrub()
            publish()
        }
    }

    fun onSpeedChange(speed: Float) {
        if (isUnavailable) return
        if (drivesPlayer) return player.setSpeed(speed)
        val book = saved ?: return
        val snapped = snapSpeed(speed)
        saved = book.copy(speed = snapped)
        publish()
        scope.launch { library.setSpeed(bookId, snapped) }
    }

    fun onChapterClick(index: Int) {
        if (isUnavailable) return
        if (drivesPlayer) return player.jumpToChapter(index)
        val chapter = saved?.book?.playableChapters?.getOrNull(index) ?: return
        saveAt { chapter.start }
    }

    /** The swatch button: the book takes the candidate inks, shown at once, and the button offers new ones. */
    fun onInksClick() {
        val chosen = candidateInks.value
        bookInks = chosen
        candidateInks.value = inks.pick()
        publish()
        scope.launch { library.setInks(bookId, chosen) }
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
        ui.value = shown?.takeIf { it.book != null }?.toUi(scrubbing)?.copy(
            unavailable = bookAvailability.toUi(),
            needsFolder = sourceIsFolder,
            inks = bookInks,
        )
    }

    private fun PlaybackState.isNear(target: Duration): Boolean =
        (position - target).absoluteValue < 2.seconds

    private companion object {
        /** How long a scrubbed position is held for a player that doesn't report it. */
        val ScrubHold = 1.seconds
    }
}

private fun Availability.toUi(): UnavailableUi? = when (this) {
    Availability.Available -> null
    Availability.Missing -> UnavailableUi(
        title = "Files missing",
        message = "Bookplate can't find this book's files. If you moved or renamed them, relink the book to where " +
            "they are now.",
    )
    Availability.AccessLost -> UnavailableUi(
        title = "Access lost",
        message = "Bookplate is no longer allowed to open this book's files. Relink the book by picking them again.",
    )
}

/** As Home words import failures. */
private val FailureReason.explanation: String
    get() = when (this) {
        FailureReason.SourceMissing -> "The file or folder is gone."
        FailureReason.AccessLost -> "Bookplate can no longer open it."
        FailureReason.Unreadable -> "A file couldn't be read."
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
        inks = book.inks,
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
    availability: BookAvailability,
    relinker: BookRelinker,
    inks: BookInkPicker,
): Saver<PlayerScreenState, List<Any>> = Saver(
    save = { state ->
        val candidate = state.candidateInks.value
        listOf(state.sheet.value?.ordinal ?: -1, candidate.accent, candidate.accentVariant)
    },
    restore = { saved ->
        PlayerScreenState(
            scope, bookId, player, library, availability, relinker, inks,
            sheet = PlayerSheet.entries.getOrNull(saved[0] as Int),
            candidateInks = BookInks(accent = saved[1] as String, accentVariant = saved[2] as String),
        )
    },
)

@Composable
fun rememberPlayerScreenState(
    bookId: BookId,
    player: BookPlayer = koinInject(),
    library: LibraryRepository = koinInject(),
    availability: BookAvailability = koinInject(),
    relinker: BookRelinker = koinInject(),
    inks: BookInkPicker = koinInject(),
): PlayerScreenState {
    val scope = rememberCoroutineScope()
    val saver = remember(scope, bookId, player, library, availability, relinker, inks) {
        playerScreenStateSaver(scope, bookId, player, library, availability, relinker, inks)
    }
    return rememberSaveable(scope, bookId, player, library, availability, relinker, inks, saver = saver) {
        PlayerScreenState(scope, bookId, player, library, availability, relinker, inks)
    }
}
