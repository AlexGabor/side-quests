package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.Reader
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** `mvhd`, else the sound track's `mdhd`, else its `tkhd` in the movie timescale. */
internal object Mp4Duration {

    fun of(reader: Reader, moov: List<Atom>, tracks: List<Track>): Duration? {
        val movie = moov.leaf("mvhd")?.let { Mp4Tracks.timeHeader(Atoms.payload(reader, it, Mp4Tracks.BOX_HEADER_MAX)) }
        movie?.let { duration(it.duration, it.timescale) }?.let { return it }
        val sound = tracks.firstOrNull { it.handler == SOUND } ?: return null
        sound.media?.let { duration(it.duration, it.timescale) }?.let { return it }
        return duration(sound.headerDuration, movie?.timescale)
    }

    /** [value] ÷ [timescale], as whole seconds plus the remainder so 64-bit values can't overflow. */
    fun duration(value: Long?, timescale: Long?): Duration? {
        if (value == null || timescale == null || value <= 0 || timescale <= 0) return null
        return (value / timescale).seconds + (value % timescale * 1_000_000_000 / timescale).nanoseconds
    }

    const val SOUND = "soun"
}
