package com.alexgabor.bookplate.core.player

import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * [AudioOutput] whose files play against [now], the test's virtual clock: while playing, the position
 * moves on by the elapsed time times the speed, through one file into the next, and ends after the last.
 * The `platform*` methods act like the platform stopping or starting playback by itself.
 */
class FakeAudioOutput(private val now: () -> Duration) : AudioOutput {

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: SharedFlow<Unit> = mutableChanges

    var files: List<OutputFile> = emptyList()
        private set
    var speed: Float = 1f
        private set
    var isPlaying: Boolean = false
        private set
    var isFailed: Boolean = false
        private set

    /** Asked to play but held back, as during a transient focus loss. */
    var isSuppressed: Boolean = false
        private set

    /** Every position the engine seeked to, in order. */
    val seeks = mutableListOf<FilePosition>()

    private var position = FilePosition(0, Duration.ZERO)
    private var since = Duration.ZERO

    override suspend fun load(files: List<OutputFile>, at: FilePosition, speed: Float) {
        this.files = files
        this.speed = speed
        position = at
        isPlaying = false
        isFailed = false
    }

    override suspend fun play() {
        advance()
        isFailed = false
        isPlaying = true
    }

    override suspend fun pause() {
        advance()
        isPlaying = false
    }

    override suspend fun seekTo(at: FilePosition) {
        advance()
        seeks += at
        position = at
    }

    override suspend fun setSpeed(speed: Float) {
        advance()
        this.speed = speed
    }

    override suspend fun clear() {
        files = emptyList()
        isPlaying = false
        position = FilePosition(0, Duration.ZERO)
    }

    override suspend fun status(): OutputStatus {
        advance()
        return OutputStatus(position, isPlaying = isPlaying && !isSuppressed && !isEnded, isEnded = isEnded, isFailed = isFailed)
    }

    /** Pauses by itself, as for headphones unplugged or the notification's pause. */
    fun platformPause() {
        advance()
        isPlaying = false
        mutableChanges.tryEmit(Unit)
    }

    /** Starts by itself, as after a transient focus loss or from the notification. */
    fun platformPlay() {
        advance()
        isPlaying = true
        mutableChanges.tryEmit(Unit)
    }

    /** Holds playback back or lets it go on, as a transient focus loss and its end do. */
    fun platformSuppress(suppressed: Boolean) {
        advance()
        isSuppressed = suppressed
        mutableChanges.tryEmit(Unit)
    }

    /** Moves by itself, as the media panel's seek bar or skip buttons do. */
    fun platformSeek(at: FilePosition) {
        advance()
        position = at
        mutableChanges.tryEmit(Unit)
    }

    /** Reports a change without making one, as a late listener call does. */
    fun signalChange() {
        mutableChanges.tryEmit(Unit)
    }

    /** Stops on an error, as for a file that can't be read. */
    fun platformFail() {
        advance()
        isPlaying = false
        isFailed = true
        mutableChanges.tryEmit(Unit)
    }

    private val isEnded: Boolean
        get() = files.isNotEmpty() && position.index == files.lastIndex && position.offset >= files.last().duration

    private fun advance() {
        val time = now()
        if (isPlaying && !isSuppressed && files.isNotEmpty()) {
            var left = (time - since) * speed.toDouble()
            var at = position
            while (left.isPositive()) {
                val remaining = files[at.index].duration - at.offset
                if (left < remaining) {
                    at = at.copy(offset = at.offset + left)
                    break
                }
                left -= remaining
                at = if (at.index == files.lastIndex) {
                    FilePosition(at.index, files[at.index].duration).also { left = Duration.ZERO }
                } else {
                    FilePosition(at.index + 1, Duration.ZERO)
                }
            }
            position = at
        }
        since = time
    }
}
