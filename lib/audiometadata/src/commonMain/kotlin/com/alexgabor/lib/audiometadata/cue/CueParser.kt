package com.alexgabor.lib.audiometadata.cue

import com.alexgabor.lib.audiometadata.CueSheet
import com.alexgabor.lib.audiometadata.Text
import com.alexgabor.lib.audiometadata.id3.Id3Text
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Cue sheet text → [CueSheet]. Reads `TITLE`, `PERFORMER`, `FILE`, `TRACK` and `INDEX`; ignores the rest. */
internal object CueParser {

    fun parse(bytes: ByteArray): CueSheet? {
        var sheetTitle: String? = null
        var sheetPerformer: String? = null
        val files = mutableListOf<FileBuilder>()
        var track: TrackBuilder? = null
        var anyTrack = false

        for (line in decode(bytes).lineSequence()) {
            val words = words(line)
            if (words.isEmpty()) continue
            val args = words.drop(1)
            when (words[0].uppercase()) {
                "TITLE" -> if (track != null) track.title = args.firstOrNull() else sheetTitle = args.firstOrNull()
                "PERFORMER" -> if (track != null) track.performer = args.firstOrNull() else sheetPerformer = args.firstOrNull()
                "FILE" -> {
                    files += FileBuilder(args.firstOrNull())
                    track = null
                }
                "TRACK" -> {
                    if (files.isEmpty()) files += FileBuilder(null)
                    track = TrackBuilder(args.firstOrNull()?.toIntOrNull() ?: 0).also { files.last().tracks += it }
                    anyTrack = true
                }
                "INDEX" -> {
                    val number = args.getOrNull(0)?.toIntOrNull() ?: continue
                    val time = args.getOrNull(1)?.let(::time) ?: continue
                    track?.indexes?.getOrPut(number) { time }
                }
            }
        }
        if (!anyTrack) return null
        return CueSheet(
            title = sheetTitle.clean(),
            performer = sheetPerformer.clean(),
            files = files.map { file -> CueSheet.File(file.name.clean(), file.tracks.map(TrackBuilder::build)) },
        )
    }

    /** `mm:ss:ff`, at 75 frames per second. Minutes may run past 99. */
    fun time(value: String): Duration? {
        val parts = value.split(':')
        if (parts.size != 3) return null
        val (m, s, f) = parts.map { it.trim().toLongOrNull() ?: return null }
        if (m < 0 || s !in 0..59 || f !in 0..74 || m > MAX_MINUTES) return null
        val frames = (m * 60 + s) * FRAMES + f
        return (frames / FRAMES).seconds + (frames % FRAMES * 1_000_000_000 / FRAMES).nanoseconds
    }

    /**
     * UTF-8 with a BOM, UTF-16 with a BOM or with NULs in alternate bytes, else UTF-8 when it decodes
     * cleanly, else Windows-1252.
     */
    fun decode(b: ByteArray): String {
        fun at(i: Int) = if (i < b.size) b[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> b.decodeToString(3, b.size)
            at(0) == 0xFF && at(1) == 0xFE || at(0) == 0xFE && at(1) == 0xFF -> Id3Text.decode(b, 0, b.size, 1)
            b.size >= 4 && at(1) == 0 && at(3) == 0 && at(0) != 0 -> Id3Text.decode(b, 0, b.size, 1)
            b.size >= 4 && at(0) == 0 && at(2) == 0 && at(1) != 0 -> Id3Text.decode(b, 0, b.size, 2)
            else -> Text.utf8OrWindows1252(b)
        }
    }

    /** A line's words: quoted values (which may hold spaces, and run to the end when unclosed) or bare. */
    private fun words(line: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c.isWhitespace() || c == '\u0000' -> i++
                c == '"' -> {
                    val close = line.indexOf('"', i + 1).let { if (it < 0) line.length else it }
                    out += line.substring(i + 1, close)
                    i = close + 1
                }
                else -> {
                    var end = i
                    while (end < line.length && !line[end].isWhitespace()) end++
                    out += line.substring(i, end)
                    i = end
                }
            }
            if (out.size == 1 && out[0].uppercase() == "REM") return emptyList()
        }
        return out
    }

    private class FileBuilder(val name: String?) {
        val tracks = mutableListOf<TrackBuilder>()
    }

    private class TrackBuilder(val number: Int) {
        var title: String? = null
        var performer: String? = null
        val indexes = LinkedHashMap<Int, Duration>()

        fun build() = CueSheet.Track(
            number = number,
            title = title.clean(),
            performer = performer.clean(),
            start = indexes[1] ?: indexes.minByOrNull { it.key }?.value,
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private const val FRAMES = 75L
    private const val MAX_MINUTES = 1_000_000L
}
