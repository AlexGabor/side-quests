package com.alexgabor.lib.audiometadata

import kotlin.time.Duration

/**
 * Chapters from sources that only give start times: Vorbis `CHAPTERnnn` comments, the FLAC CUESHEET
 * block and cue sheets. Each ends where the next starts.
 */
internal object FlatChapters {

    class Entry(val title: String?, val start: Duration)

    /**
     * [entries] sorted by start, keeping the first of any with the same start. The last ends at
     * [lastEnd], else at [duration], whichever is first after its start, else at its start.
     */
    fun build(entries: List<Entry>, duration: Duration?, lastEnd: Duration? = null): List<Chapter> {
        val sorted = entries.take(MAX_CHAPTERS).sortedBy { it.start }.distinctBy { it.start }
        return sorted.mapIndexed { i, entry ->
            val end = sorted.getOrNull(i + 1)?.start
                ?: lastEnd?.takeIf { it > entry.start }
                ?: duration?.takeIf { it > entry.start }
                ?: entry.start
            Chapter(entry.title?.trim()?.takeIf { it.isNotEmpty() }, entry.start, end)
        }
    }

    const val MAX_CHAPTERS = 10_000
}
