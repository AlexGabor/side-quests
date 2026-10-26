package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookDetails
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The [BookPlayer] that plays audio, through a platform [output]. It holds every playback rule (one
 * timeline across the book's files, clamping, chapters, saving and restoring progress, the end of the book)
 * so each platform only has to play files.
 *
 * Commands, polls and the output's [AudioOutput.changes] run one at a time, in order, on [scope] with
 * `CoroutineDispatchers.Default`, read when the first command arrives. While playing, the output is polled
 * every [PollInterval] and the position saved every [SaveInterval]. The position is also saved on each
 * emission of [backgrounded], which the platform sends when the app goes to the background.
 */
class EngineBookPlayer(
    private val repository: LibraryRepository,
    private val output: AudioOutput,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val backgrounded: Flow<Unit> = emptyFlow(),
) : BookPlayer {

    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val processor: Job by lazy {
        scope.launch(CoroutineDispatchers.Default) {
            launch { output.changes.collect { commands.trySend { reconcile(save = true) } } }
            launch { backgrounded.collect { commands.trySend { saveNow() } } }
            for (command in commands) command()
        }
    }

    private var ticker: Job? = null
    private var polls = 0

    /** The loaded book's files; `null` with no book loaded. */
    private var timeline: BookTimeline? = null

    override fun load(id: BookId) = enqueue {
        val current = mutableState.value
        if (current.book?.id == id) return@enqueue
        if (current.isPlaying) stopPlaying() else current.book?.let { save(it.id) }
        val details = repository.book(id).first()
        if (details == null) {
            timeline = null
            mutableState.value = PlaybackState()
            output.clear()
            return@enqueue
        }
        val loaded = details.toState()
        val bookTimeline = BookTimeline(details.files, loaded.duration)
        timeline = bookTimeline
        mutableState.value = loaded
        val at = bookTimeline.locate(loaded.position)
        if (at == null) {
            output.clear()
        } else {
            val book = details.book
            val files = bookTimeline.files.map { OutputFile(it.ref, it.duration, book.title, book.author) }
            output.load(files, at, loaded.speed)
        }
    }

    override fun play() = enqueue {
        val current = mutableState.value
        val book = current.book ?: return@enqueue
        val bookTimeline = timeline ?: return@enqueue
        if (current.isPlaying || bookTimeline.files.isEmpty()) return@enqueue
        if (current.position >= book.duration) {
            mutableState.update { it.copy(position = Duration.ZERO) }
            output.seekTo(bookTimeline.locate(Duration.ZERO) ?: return@enqueue)
        }
        output.play()
        startPlaying(book.id)
    }

    override fun pause() = enqueue {
        if (mutableState.value.isPlaying) stopPlaying()
    }

    override fun seekTo(position: Duration) = enqueue { moveTo(position) }

    override fun skipBack() = enqueue { moveTo(currentPosition() - SkipInterval) }

    override fun skipForward() = enqueue { moveTo(currentPosition() + SkipInterval) }

    override fun setSpeed(speed: Float) = enqueue {
        val book = mutableState.value.book ?: return@enqueue
        val snapped = snapSpeed(speed)
        mutableState.update { it.copy(speed = snapped) }
        output.setSpeed(snapped)
        repository.setSpeed(book.id, snapped)
    }

    override fun jumpToChapter(index: Int) = enqueue {
        val chapter = mutableState.value.book?.playableChapters?.getOrNull(index) ?: return@enqueue
        moveTo(chapter.start)
    }

    private fun enqueue(command: suspend () -> Unit) {
        processor
        commands.trySend(command)
    }

    /** Moves to [position] within the book and saves it; reaching the end pauses. */
    private suspend fun moveTo(position: Duration) {
        val book = mutableState.value.book ?: return
        val clamped = position.coerceIn(Duration.ZERO, book.duration)
        mutableState.update { it.copy(position = clamped) }
        timeline?.locate(clamped)?.let { output.seekTo(it) }
        if (clamped == book.duration && mutableState.value.isPlaying) stopPlaying() else save(book.id)
    }

    /**
     * Brings the state in line with what the output is doing now. Reading the output's current status,
     * rather than trusting what a change said, keeps a late change from undoing a later command.
     *
     * With [save], for a change the platform signalled, the position is saved: the platform may have seeked
     * by itself (the media panel's seek bar or skip buttons, a headset button). A poll may already have
     * caught up with such a seek, so the position isn't compared with the state's.
     */
    private suspend fun reconcile(save: Boolean = false) {
        val current = mutableState.value
        val book = current.book ?: return
        val bookTimeline = timeline ?: return
        if (bookTimeline.files.isEmpty()) return
        val status = output.status()
        if (status.isEnded) {
            mutableState.update { it.copy(position = book.duration) }
            if (current.isPlaying) stopPlaying(syncPosition = false)
            return
        }
        mutableState.update { it.copy(position = bookTimeline.positionOf(status.position)) }
        when {
            // A transient focus loss leaves the output asked to play, so it resumes by itself afterwards.
            current.isPlaying && !status.isPlaying -> stopPlaying(syncPosition = false, pauseOutput = status.isFailed)
            !current.isPlaying && status.isPlaying && !status.isFailed -> startPlaying(book.id)
            save -> save(book.id)
        }
    }

    private suspend fun tick() {
        if (!mutableState.value.isPlaying) return
        reconcile()
        val current = mutableState.value
        val book = current.book ?: return
        if (current.isPlaying && ++polls % SavePolls == 0) save(book.id)
    }

    private suspend fun startPlaying(id: BookId) {
        mutableState.update { it.copy(isPlaying = true) }
        repository.setLastPlayed(id, clock.now())
        polls = 0
        ticker?.cancel()
        ticker = scope.launch(CoroutineDispatchers.Default) {
            while (isActive) {
                delay(PollInterval)
                commands.trySend { tick() }
            }
        }
    }

    /**
     * Pauses, saving the position and the time it stopped. The output is paused too unless [pauseOutput] is
     * false: after the end or an error it must be, so it doesn't start again by itself on a later seek.
     */
    private suspend fun stopPlaying(syncPosition: Boolean = true, pauseOutput: Boolean = true) {
        ticker?.cancel()
        ticker = null
        if (syncPosition) syncPosition()
        if (pauseOutput) output.pause()
        mutableState.update { it.copy(isPlaying = false) }
        val book = mutableState.value.book ?: return
        save(book.id)
        repository.setLastPlayed(book.id, clock.now())
    }

    /** The output's position while playing, which the state only catches up with on each poll. */
    private suspend fun currentPosition(): Duration {
        if (mutableState.value.isPlaying) syncPosition()
        return mutableState.value.position
    }

    private suspend fun syncPosition() {
        val bookTimeline = timeline ?: return
        if (bookTimeline.files.isEmpty()) return
        val status = output.status()
        val position = if (status.isEnded) bookTimeline.duration else bookTimeline.positionOf(status.position)
        mutableState.update { it.copy(position = position) }
    }

    private suspend fun saveNow() {
        val book = mutableState.value.book ?: return
        currentPosition()
        save(book.id)
    }

    private suspend fun save(id: BookId) {
        repository.setPosition(id, mutableState.value.position)
    }

    companion object {
        /** How often the output is asked where it is while playing. */
        val PollInterval: Duration = 250.milliseconds

        /** How much playback may pass between saves. */
        val SaveInterval: Duration = 5.seconds

        private val SavePolls = (SaveInterval / PollInterval).toInt()
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
    )
    return PlaybackState(
        book = book,
        position = this.book.position.coerceIn(Duration.ZERO, book.duration),
        isPlaying = false,
        speed = snapSpeed(this.book.speed),
    )
}
