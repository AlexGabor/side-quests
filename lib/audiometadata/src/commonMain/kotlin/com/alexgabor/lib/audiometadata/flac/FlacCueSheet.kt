package com.alexgabor.lib.audiometadata.flac

import com.alexgabor.lib.audiometadata.Chapter
import com.alexgabor.lib.audiometadata.CueSheet
import com.alexgabor.lib.audiometadata.FlatChapters
import com.alexgabor.lib.audiometadata.durationOf
import com.alexgabor.lib.audiometadata.u64
import com.alexgabor.lib.audiometadata.u8
import kotlin.time.Duration

/**
 * The CUESHEET block: tracks with sample offsets and their index points, but no titles. Titles come
 * from an embedded cue sheet (the `CUESHEET` comment) with the same track numbers.
 */
internal object FlacCueSheet {

    fun chapters(b: ByteArray, sampleRate: Long, titles: CueSheet?, duration: Duration?): List<Chapter> {
        if (sampleRate <= 0 || b.size < TRACKS) return emptyList()
        val leadOut = if (b.u8(CD_FLAG) and 0x80 != 0) 170 else 255
        val count = b.u8(TRACK_COUNT)
        val byNumber = titles?.files.orEmpty().flatMap { it.tracks }.associateBy { it.number }
        val entries = mutableListOf<FlatChapters.Entry>()
        var end: Duration? = null
        var p = TRACKS
        repeat(count) {
            if (p + TRACK_SIZE > b.size) return@repeat
            val offset = b.u64(p)
            val number = b.u8(p + 8)
            val indexCount = b.u8(p + 35)
            val indexes = (0 until indexCount).mapNotNull { i ->
                val q = p + TRACK_SIZE + i * INDEX_SIZE
                if (q + INDEX_SIZE > b.size) null else b.u8(q + 8) to b.u64(q)
            }
            p += TRACK_SIZE + indexCount * INDEX_SIZE
            if (offset < 0) return@repeat
            if (number == leadOut) {
                end = time(offset, sampleRate)
                return@repeat
            }
            val index = indexes.firstOrNull { it.first == 1 } ?: indexes.minByOrNull { it.first }
            val start = time(offset + (index?.second?.coerceAtLeast(0) ?: 0), sampleRate)
            entries += FlatChapters.Entry(byNumber[number]?.title, start)
        }
        return FlatChapters.build(entries, duration, lastEnd = end)
    }

    private fun time(samples: Long, rate: Long): Duration = durationOf(samples, rate) ?: Duration.ZERO

    private const val CD_FLAG = 128 + 8
    private const val TRACK_COUNT = CD_FLAG + 1 + 258
    private const val TRACKS = TRACK_COUNT + 1
    private const val TRACK_SIZE = 36
    private const val INDEX_SIZE = 12
}
