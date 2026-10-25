package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * What a platform's audio stack does for [EngineBookPlayer]: play a list of files back to back at a speed.
 * It knows nothing about books, chapters or saving.
 *
 * Calls may suspend while the platform catches up; [EngineBookPlayer] makes them one at a time.
 */
interface AudioOutput {

    /**
     * Emits whenever playback changed on the platform's side without a call here: audio focus, headphones
     * unplugged, a media panel's buttons, reaching the end, an error. The engine then reads [status].
     */
    val changes: Flow<Unit>

    /** Replaces what is playing with [files], positioned at [at], at [speed], paused. */
    suspend fun load(files: List<OutputFile>, at: FilePosition, speed: Float)

    suspend fun play()

    suspend fun pause()

    suspend fun seekTo(at: FilePosition)

    suspend fun setSpeed(speed: Float)

    /** Stops and forgets the files. */
    suspend fun clear()

    /** Where playback is right now. */
    suspend fun status(): OutputStatus
}

/**
 * One file to play.
 *
 * @property title and [artist] describe the book, for the platform's media panel.
 */
data class OutputFile(
    val ref: DocumentRef,
    val duration: Duration,
    val title: String,
    val artist: String?,
)

/**
 * @property isPlaying audio is coming out or about to: asked to play and not held back by the platform (a
 *   transient focus loss shows as not playing).
 * @property isEnded the last file played to its end.
 * @property isFailed playback stopped on an error, such as a file that can't be read.
 */
data class OutputStatus(
    val position: FilePosition,
    val isPlaying: Boolean,
    val isEnded: Boolean = false,
    val isFailed: Boolean = false,
)
