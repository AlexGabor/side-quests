package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.BookAvailability
import com.alexgabor.bookplate.core.library.BookDetails
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [BookPlayer] without audio: while playing, a clock ticks every [TickInterval] and moves the position
 * on by that much times the speed. Progress is read from and saved to [repository] the way a real
 * engine does, so Home and the mini player follow it. Like the engine, playing first checks the book with
 * [availability] and stays paused when it is unavailable, a playing book is checked every [AvailabilityInterval] and
 * paused when it is unavailable, and [failPlayback] stops as an output error would, then checks the book.
 *
 * Commands and ticks run one at a time, in order, on [scope] with `CoroutineDispatchers.Default`, which
 * is read when the first command arrives.
 */
class FakeBookPlayer(
    private val repository: LibraryRepository,
    private val availability: BookAvailability,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : BookPlayer {

    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val processor: Job by lazy {
        scope.launch(CoroutineDispatchers.Default) {
            for (command in commands) command()
        }
    }

    private var ticker: Job? = null

    /** Follows the loaded book's inks; see [followInks]. */
    private var inksJob: Job? = null

    /** The position last written to the library, so playback saves every [SaveInterval]. */
    private var savedPosition = Duration.ZERO

    private var ticks = 0

    override fun load(id: BookId) = enqueue {
        val current = mutableState.value
        if (current.book?.id == id) return@enqueue
        if (current.isPlaying) stopPlaying()
        current.book?.let { save(it.id) }
        val details = repository.book(id).first()
        mutableState.value = details?.toState() ?: PlaybackState()
        savedPosition = mutableState.value.position
        followInks(details?.book?.id)
    }

    override fun play() = enqueue {
        val current = mutableState.value
        val book = current.book ?: return@enqueue
        if (current.isPlaying) return@enqueue
        if (availability.check(book.id) != Availability.Available) return@enqueue
        if (current.position >= book.duration) mutableState.update { it.copy(position = Duration.ZERO) }
        mutableState.update { it.copy(isPlaying = true) }
        repository.setLastPlayed(book.id, clock.now())
        ticks = 0
        ticker = scope.launch(CoroutineDispatchers.Default) {
            while (isActive) {
                delay(TickInterval)
                commands.trySend { tick() }
            }
        }
    }

    override fun pause() = enqueue {
        if (mutableState.value.isPlaying) stopPlaying()
    }

    /** Stops playing as the engine does when its output fails, as for a file that went missing, and checks the book. */
    fun failPlayback() = enqueue {
        val book = mutableState.value.book ?: return@enqueue
        if (mutableState.value.isPlaying) stopPlaying()
        availability.check(book.id)
    }

    override fun seekTo(position: Duration) = enqueue { moveTo(position) }

    override fun skipBack() = enqueue { moveTo(mutableState.value.position - SkipInterval) }

    override fun skipForward() = enqueue { moveTo(mutableState.value.position + SkipInterval) }

    override fun setSpeed(speed: Float) = enqueue {
        val book = mutableState.value.book ?: return@enqueue
        val snapped = snapSpeed(speed)
        mutableState.update { it.copy(speed = snapped) }
        repository.setSpeed(book.id, snapped)
    }

    override fun jumpToChapter(index: Int) = enqueue {
        val chapter = mutableState.value.book?.playableChapters?.getOrNull(index) ?: return@enqueue
        moveTo(chapter.start)
    }

    /** Follows the loaded book's inks in the library until another book is loaded, keeping [state]'s book in step. */
    private fun followInks(id: BookId?) {
        inksJob?.cancel()
        inksJob = id?.let {
            scope.launch(CoroutineDispatchers.Default) {
                repository.book(id).mapNotNull { it?.book?.inks }.distinctUntilChanged().collect { inks ->
                    enqueue {
                        mutableState.update { state ->
                            val book = state.book?.takeIf { it.id == id && it.inks != inks } ?: return@update state
                            state.copy(book = book.copy(inks = inks))
                        }
                    }
                }
            }
        }
    }

    private fun enqueue(command: suspend () -> Unit) {
        processor
        commands.trySend(command)
    }

    private suspend fun tick() {
        val current = mutableState.value
        val book = current.book ?: return
        if (!current.isPlaying) return
        // In tenths, so 0.3× moves exactly 75 ms a tick.
        val next = current.position + TickInterval * (current.speed * 10).roundToInt() / 10
        if (next >= book.duration) {
            mutableState.update { it.copy(position = book.duration) }
            stopPlaying()
            return
        }
        mutableState.update { it.copy(position = next) }
        if (next - savedPosition >= SaveInterval) save(book.id)
        if (++ticks % AvailabilityTicks == 0 && availability.check(book.id) != Availability.Available) stopPlaying()
    }

    /** Moves to [position] within the book and saves it; reaching the end pauses. */
    private suspend fun moveTo(position: Duration) {
        val book = mutableState.value.book ?: return
        val clamped = position.coerceIn(Duration.ZERO, book.duration)
        mutableState.update { it.copy(position = clamped) }
        if (clamped == book.duration && mutableState.value.isPlaying) stopPlaying() else save(book.id)
    }

    /** Pauses, saving the position and the time it stopped. */
    private suspend fun stopPlaying() {
        ticker?.cancel()
        ticker = null
        mutableState.update { it.copy(isPlaying = false) }
        val book = mutableState.value.book ?: return
        save(book.id)
        repository.setLastPlayed(book.id, clock.now())
    }

    private suspend fun save(id: BookId) {
        val position = mutableState.value.position
        repository.setPosition(id, position)
        savedPosition = position
    }

    companion object {
        /** How often the clock moves the position on while playing. */
        val TickInterval: Duration = 250.milliseconds

        /** How much book time may pass between saves while playing. */
        val SaveInterval: Duration = 5.seconds

        /** How often a playing book's files are checked, as the engine does. */
        val AvailabilityInterval: Duration = 30.seconds

        private val AvailabilityTicks = (AvailabilityInterval / TickInterval).toInt()
    }
}

private fun BookDetails.toState(): PlaybackState {
    val book = PlayerBook(
        id = book.id,
        title = book.title,
        author = book.author,
        coverPath = book.coverPath,
        duration = book.duration,
        chapters = chapters,
        inks = book.inks,
    )
    return PlaybackState(
        book = book,
        position = this.book.position.coerceIn(Duration.ZERO, book.duration),
        isPlaying = false,
        speed = snapSpeed(this.book.speed),
    )
}
