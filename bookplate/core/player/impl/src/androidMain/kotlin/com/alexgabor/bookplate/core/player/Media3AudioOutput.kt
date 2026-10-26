package com.alexgabor.bookplate.core.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [AudioOutput] over [PlaybackService]'s player, through a `MediaController`. Connecting the controller is
 * what starts the service; Media3 then moves it to the foreground while playing.
 *
 * The files are one item ([bookMediaItem]), one period each, so the player's position is in the whole book.
 * A [FilePosition] maps to it through the periods' starts once the player knows them, else the parsed
 * durations, so the offset into each file stays exact when a decoder's duration differs a little.
 *
 * The controller lives on the main thread, so every call hops to `CoroutineDispatchers.Main`. If the service
 * goes away, the next call connects again and puts back the last playlist where it was, paused.
 */
internal class Media3AudioOutput(private val context: Context) : AudioOutput {

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: SharedFlow<Unit> = mutableChanges

    private var controller: MediaController? = null

    /** What was last loaded, to put back after a reconnect. */
    private var files: List<OutputFile> = emptyList()
    private var item: MediaItem? = null
    private var speed = 1f
    private var lastPosition = FilePosition(0, Duration.ZERO)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(*WatchedEvents)) mutableChanges.tryEmit(Unit)
        }
    }

    override suspend fun load(files: List<OutputFile>, at: FilePosition, speed: Float) = withController { controller ->
        this.files = files
        val book = bookMediaItem(files)
        item = book
        this.speed = speed
        lastPosition = at
        controller.pause()
        controller.setMediaItem(book, parsedStart(at.index) + at.offset.inWholeMilliseconds)
        controller.setPlaybackSpeed(speed)
        controller.prepare()
    }

    override suspend fun play() = withController { controller ->
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override suspend fun pause() = withController { it.pause() }

    override suspend fun seekTo(at: FilePosition) = withController { controller ->
        lastPosition = at
        controller.seekTo(controller.windowPositionOf(at))
    }

    override suspend fun setSpeed(speed: Float) = withController { controller ->
        this.speed = speed
        controller.setPlaybackSpeed(speed)
    }

    override suspend fun clear() = withContext(CoroutineDispatchers.Main) {
        files = emptyList()
        item = null
        lastPosition = FilePosition(0, Duration.ZERO)
        controller?.takeIf { it.isConnected }?.run {
            stop()
            clearMediaItems()
        }
        Unit
    }

    override suspend fun status(): OutputStatus = withController { controller ->
        val position = controller.filePosition()
        lastPosition = position
        val state = controller.playbackState
        OutputStatus(
            position = position,
            isPlaying = controller.playWhenReady &&
                controller.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                state != Player.STATE_ENDED && state != Player.STATE_IDLE,
            isEnded = state == Player.STATE_ENDED,
            isFailed = controller.playerError != null,
        )
    }

    /** Where the player is, as a file and an offset into it. */
    private fun MediaController.filePosition(): FilePosition {
        val window = currentPosition
        // From the position, not `currentPeriodIndex`: right after a seek the controller already reports the new
        // position but can still report the old period.
        val starts = periodStarts() ?: files.indices.map(::parsedStart)
        val index = starts.indexOfLast { it <= window }.coerceAtLeast(0)
        val start = starts.getOrElse(index) { 0L }
        return FilePosition(index, (window - start).coerceAtLeast(0).milliseconds)
    }

    private fun MediaController.windowPositionOf(at: FilePosition): Long =
        (periodStarts()?.getOrNull(at.index) ?: parsedStart(at.index)) + at.offset.inWholeMilliseconds

    /** Each file's start in the item, from the player's timeline, or `null` while it doesn't have one period per file. */
    private fun MediaController.periodStarts(): List<Long>? {
        val timeline = currentTimeline
        if (timeline.isEmpty || timeline.periodCount != files.size || files.isEmpty()) return null
        val period = Timeline.Period()
        return List(files.size) { timeline.getPeriod(it, period).positionInWindowMs }
    }

    /** Where file [index] starts by the parsed durations. */
    private fun parsedStart(index: Int): Long = files.take(index.coerceAtLeast(0)).sumOf { it.duration.inWholeMilliseconds }

    private suspend fun <T> withController(block: (MediaController) -> T): T =
        withContext(CoroutineDispatchers.Main) { block(connected()) }

    private suspend fun connected(): MediaController {
        controller?.let { if (it.isConnected) return it else it.release() }
        controller = null
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val connected = MediaController.Builder(context, token).buildAsync().await()
        connected.addListener(listener)
        controller = connected
        // A fresh service has an empty player: put back what the engine thinks is loaded.
        val book = item
        if (book != null && connected.mediaItemCount == 0) {
            connected.setMediaItem(book, parsedStart(lastPosition.index) + lastPosition.offset.inWholeMilliseconds)
            connected.setPlaybackSpeed(speed)
            connected.prepare()
        }
        return connected
    }

    private companion object {
        val WatchedEvents = intArrayOf(
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAYER_ERROR,
            // The panel's seek bar and skip buttons, and headset buttons, seek by themselves.
            Player.EVENT_POSITION_DISCONTINUITY,
        )
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            }
        },
        MoreExecutors.directExecutor(),
    )
    continuation.invokeOnCancellation { cancel(false) }
}
