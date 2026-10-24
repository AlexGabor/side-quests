package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.durationOf
import kotlin.time.Duration

/** `mvhd`, else the sound track's `mdhd`, else its `tkhd` in the movie timescale. */
internal object Mp4Duration {

    fun of(reader: Reader, moov: List<Atom>, tracks: List<Track>): Duration? {
        val movie = moov.leaf("mvhd")?.let { Mp4Tracks.timeHeader(Atoms.payload(reader, it, Mp4Tracks.BOX_HEADER_MAX)) }
        movie?.let { duration(it.duration, it.timescale) }?.let { return it }
        val sound = tracks.firstOrNull { it.handler == SOUND } ?: return null
        sound.media?.let { duration(it.duration, it.timescale) }?.let { return it }
        return duration(sound.headerDuration, movie?.timescale)
    }

    fun duration(value: Long?, timescale: Long?): Duration? = durationOf(value, timescale)

    const val SOUND = "soun"
}
