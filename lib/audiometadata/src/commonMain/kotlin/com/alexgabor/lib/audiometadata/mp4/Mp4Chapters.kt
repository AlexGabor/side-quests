package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.Chapter
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.id3.Id3Text
import com.alexgabor.lib.audiometadata.u16
import com.alexgabor.lib.audiometadata.u32
import com.alexgabor.lib.audiometadata.u8
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Chapters from a QuickTime chapter track (`tref/chap` → a text track) or, failing that, a Nero `chpl`.
 * Both are flat. Each source is guarded on its own, so a broken chapter track falls back to `chpl`.
 */
internal object Mp4Chapters {

    fun read(reader: Reader, tracks: List<Track>, udta: List<Atom>, duration: Duration?): List<Chapter> {
        val quickTime = guarded { quickTime(reader, tracks) }.orEmpty()
        val raw = quickTime.ifEmpty { guarded { nero(reader, udta) }.orEmpty() }
        return raw.mapIndexed { i, chapter ->
            val end = raw.getOrNull(i + 1)?.start
                ?: duration?.takeIf { it > chapter.start }
                ?: (chapter.start + (chapter.length ?: Duration.ZERO))
            Chapter(chapter.title, chapter.start, end)
        }
    }

    private class Raw(val title: String?, val start: Duration, val length: Duration?)

    // QuickTime chapter track

    private fun quickTime(reader: Reader, tracks: List<Track>): List<Raw> {
        val track = tracks.asSequence().flatMap { it.chapterRefs }
            .firstNotNullOfOrNull { id -> tracks.firstOrNull { it.id == id && it.handler in TEXT_HANDLERS } }
            ?: return emptyList()
        val timescale = track.media?.timescale?.takeIf { it > 0 } ?: return emptyList()
        val stbl = track.stbl ?: return emptyList()
        val tables = Atoms.children(reader, stbl)
        fun table(type: String) = tables.leaf(type)?.let { Atoms.payload(reader, it, MAX_TABLE) }

        val sizes = table("stsz")?.let(::sampleSizes) ?: return emptyList()
        val deltas = table("stts")?.let { sampleDeltas(it, sizes.size) } ?: return emptyList()
        val runs = table("stsc")?.let(::chunkRuns) ?: return emptyList()
        val offsets = table("stco")?.let { chunkOffsets(it, wide = false) }
            ?: table("co64")?.let { chunkOffsets(it, wide = true) }
            ?: return emptyList()

        val titles = arrayOfNulls<String>(sizes.size)
        var anyRead = false
        forEachChunk(sizes, runs, offsets) { first, count, offset ->
            if (readTitles(reader, sizes, first, count, offset, titles)) anyRead = true
        }
        if (!anyRead) return emptyList()

        val count = minOf(sizes.size, deltas.size)
        var time = 0L
        return List(count) { i ->
            val start = Mp4Duration.duration(time, timescale) ?: Duration.ZERO
            time += deltas[i]
            Raw(titles[i], start, Mp4Duration.duration(deltas[i], timescale))
        }
    }

    /** `stsz`: one size for every sample, or a table of sizes. */
    private fun sampleSizes(b: ByteArray): LongArray? {
        if (b.size < 12) return null
        val uniform = b.u32(4)
        val count = b.u32(8)
        val n = if (uniform != 0L) minOf(count, MAX_CHAPTERS.toLong()) else minOf(count, MAX_CHAPTERS.toLong(), (b.size - 12) / 4L)
        return LongArray(n.toInt()) { if (uniform != 0L) uniform else b.u32(12 + it * 4) }
    }

    /** `stts`: runs of (sample count, duration), expanded to one duration per sample. */
    private fun sampleDeltas(b: ByteArray, samples: Int): LongArray? {
        if (b.size < 8) return null
        val entries = minOf(b.u32(4), (b.size - 8) / 8L).toInt()
        val out = LongArray(samples)
        var i = 0
        for (e in 0 until entries) {
            val run = b.u32(8 + e * 8)
            val delta = b.u32(12 + e * 8)
            var k = 0L
            while (k < run && i < samples) { out[i++] = delta; k++ }
            if (i == samples) break
        }
        return out.copyOf(i)
    }

    /** `stsc`: (first chunk, samples per chunk) runs, chunks numbered from 1. */
    private fun chunkRuns(b: ByteArray): List<Pair<Long, Long>>? {
        if (b.size < 8) return null
        val entries = minOf(b.u32(4), (b.size - 8) / 12L).toInt()
        return List(entries) { b.u32(8 + it * 12) to b.u32(12 + it * 12) }
    }

    /** `stco` (32-bit) or `co64` (64-bit) chunk offsets. */
    private fun chunkOffsets(b: ByteArray, wide: Boolean): LongArray? {
        if (b.size < 8) return null
        val width = if (wide) 8 else 4
        val entries = minOf(b.u32(4), (b.size - 8L) / width, MAX_CHAPTERS.toLong()).toInt()
        return LongArray(entries) { if (wide) b.u64(8 + it * 8) else b.u32(8 + it * 4) }
    }

    /** Calls [block] with each chunk's first sample, its sample count and its file offset. */
    private inline fun forEachChunk(sizes: LongArray, runs: List<Pair<Long, Long>>, offsets: LongArray, block: (Int, Int, Long) -> Unit) {
        var sample = 0
        var run = 0
        for (chunk in offsets.indices) {
            if (sample >= sizes.size) break
            while (run + 1 < runs.size && runs[run + 1].first <= chunk + 1) run++
            val perChunk = runs.getOrNull(run)?.second ?: 0L
            val count = minOf(perChunk, (sizes.size - sample).toLong()).toInt()
            if (count > 0) block(sample, count, offsets[chunk])
            sample += count
        }
    }

    /**
     * Reads the titles of one chunk's samples into [titles]: in one read when they are small together,
     * else each on its own, never more than [MAX_SAMPLE] bytes of a sample. True when any sample was read.
     */
    private fun readTitles(reader: Reader, sizes: LongArray, first: Int, count: Int, offset: Long, titles: Array<String?>): Boolean {
        if (offset < 0) return false
        var total = 0L
        for (i in first until first + count) total += sizes[i]
        var anyRead = false
        if (total <= MAX_CHUNK_READ) {
            val chunk = reader.bytes(offset, total.toInt())
            var at = 0
            for (i in first until first + count) {
                val end = minOf(at + sizes[i].toInt(), chunk.size)
                if (end - at >= 2) { titles[i] = title(chunk, at, end); anyRead = true }
                at += sizes[i].toInt()
            }
        } else {
            var position = offset
            for (i in first until first + count) {
                val sample = reader.bytes(position, minOf(sizes[i], MAX_SAMPLE.toLong()).toInt())
                if (sample.size >= 2) { titles[i] = title(sample, 0, sample.size); anyRead = true }
                position += sizes[i]
            }
        }
        return anyRead
    }

    /** A text sample: 16-bit length, then UTF-8, or UTF-16 when it starts with a byte-order mark. */
    private fun title(b: ByteArray, start: Int, end: Int): String? {
        val length = b.u16(start)
        val from = start + 2
        val to = minOf(end, from + length)
        val utf16 = to - from >= 2 && ((b.u8(from) == 0xFE && b.u8(from + 1) == 0xFF) || (b.u8(from) == 0xFF && b.u8(from + 1) == 0xFE))
        return Id3Text.decode(b, from, to, if (utf16) 1 else 3).trim().trimEnd('\u0000').takeIf { it.isNotEmpty() }
    }

    // Nero chpl

    /** `chpl`: version, flags, 4 reserved bytes in version 1, a count, then (start in 100 ns, title). */
    private fun nero(reader: Reader, udta: List<Atom>): List<Raw> {
        val chpl = udta.leaf("chpl") ?: return emptyList()
        val b = Atoms.payload(reader, chpl, MAX_TABLE)
        if (b.isEmpty()) return emptyList()
        var p = if (b.u8(0) == 1) 8 else 4
        if (p >= b.size) return emptyList()
        val count = b.u8(p++)
        val chapters = mutableListOf<Raw>()
        for (i in 0 until count) {
            if (p + 9 > b.size) break
            val start = b.u64(p)
            val length = b.u8(p + 8)
            p += 9
            if (p + length > b.size) break
            val title = b.decodeToString(p, p + length).trim().takeIf { it.isNotEmpty() }
            p += length
            if (start >= 0) chapters += Raw(title, (start / 10).microseconds, null)
        }
        return chapters.sortedBy { it.start }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private val TEXT_HANDLERS = setOf("text", "sbtl")
    private const val MAX_CHAPTERS = 10_000
    private const val MAX_TABLE = 4 * 1024 * 1024
    private const val MAX_CHUNK_READ = 64 * 1024
    private const val MAX_SAMPLE = 2 + 1024
}
