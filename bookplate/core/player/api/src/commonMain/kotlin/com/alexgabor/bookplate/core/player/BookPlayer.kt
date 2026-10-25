package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.BookId
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays one book at a time. A single instance is shared by every screen and, later, the media panel.
 *
 * Positions are in the book's timeline: a book of several files is played as one, and the engine maps a
 * position to a file and an offset in it.
 *
 * Commands return at once and run in the order they were called; their effect shows up in [state]. Each
 * one saves the book's progress to the library as it needs to: speed per book, position, and the
 * last-played time when playback starts or stops.
 */
interface BookPlayer {

    val state: StateFlow<PlaybackState>

    /**
     * Loads book [id] at its saved position and speed, paused. Does nothing when it is already loaded. A
     * book that was playing is paused and saved first. An id the library doesn't know leaves no book loaded.
     */
    fun load(id: BookId)

    /** Plays the loaded book; from the start when it is at its end. Does nothing with no book loaded. */
    fun play()

    fun pause()

    /** Moves to [position], clamped to the book. */
    fun seekTo(position: Duration)

    /** Moves [SkipInterval] back, stopping at the start. */
    fun skipBack()

    /** Moves [SkipInterval] forward, stopping at the end. */
    fun skipForward()

    /** Sets and saves the loaded book's speed, through [snapSpeed]. */
    fun setSpeed(speed: Float)

    /** Moves to the start of the playable chapter at [index]; an index out of range does nothing. */
    fun jumpToChapter(index: Int)
}

/** How far [BookPlayer.skipBack] and [BookPlayer.skipForward] move. */
val SkipInterval: Duration = 10.seconds

/** The speeds a book can play at. */
val SpeedRange: ClosedFloatingPointRange<Float> = 0.3f..3f

/** [speed] rounded to the nearest tenth and clamped to [SpeedRange]; `NaN` is 1×. */
fun snapSpeed(speed: Float): Float {
    if (speed.isNaN()) return 1f
    return round(speed.coerceIn(SpeedRange) * 10) / 10f
}
