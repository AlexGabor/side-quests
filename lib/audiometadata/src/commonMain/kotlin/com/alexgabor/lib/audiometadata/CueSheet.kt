package com.alexgabor.lib.audiometadata

import com.alexgabor.lib.audiometadata.cue.CueParser
import kotlin.time.Duration

/**
 * A standalone `.cue` sheet: the sheet's title and performer, and its tracks grouped by the audio
 * file they belong to. Finding the sheet next to an audio file is up to the caller.
 */
data class CueSheet(
    val title: String?,
    val performer: String?,
    val files: List<File>,
) {
    /** A `FILE` entry. [name] is `null` for tracks listed before any `FILE`. */
    data class File(val name: String?, val tracks: List<Track>)

    /** A `TRACK`. [start] is its `INDEX 01`, else its lowest index, or `null` when it has none. */
    data class Track(val number: Int, val title: String?, val performer: String?, val start: Duration?)

    /**
     * Chapters for one audio file. The entry is the one named [audioFileName] (case and directories
     * ignored, or else extensions too, since sheets often name the `.wav` a file was encoded from), or
     * the only entry when there is just one. Each track with a start is a chapter, titled by its
     * `TITLE`; the last ends at [duration]. Empty when no entry matches.
     */
    fun chapters(audioFileName: String?, duration: Duration?): List<Chapter> {
        val file = files.singleOrNull() ?: audioFileName?.let(::match) ?: return emptyList()
        return chaptersOf(file, duration)
    }

    internal fun chaptersOf(file: File, duration: Duration?): List<Chapter> = FlatChapters.build(
        file.tracks.mapNotNull { track -> track.start?.let { FlatChapters.Entry(track.title, it) } },
        duration,
    )

    private fun match(audioFileName: String): File? {
        val wanted = baseName(audioFileName)
        val named = files.filter { it.name != null }
        return named.firstOrNull { baseName(it.name!!) == wanted }
            ?: named.firstOrNull { stem(baseName(it.name!!)) == stem(wanted) }
    }

    private fun baseName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\').trim().lowercase()

    private fun stem(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    companion object {
        /**
         * The sheet in [bytes], decoded from UTF-8, UTF-16 or Windows-1252 (see the spec). `null` when the
         * text holds no `TRACK`. Never throws.
         */
        fun parse(bytes: ByteArray): CueSheet? = try {
            CueParser.parse(bytes)
        } catch (_: Exception) {
            null
        }

        /** [parse] over the first [MAX_SIZE] bytes of [source]. Never throws. */
        fun read(source: ByteSource): CueSheet? = try {
            parse(Reader(source).bytes(0, MAX_SIZE))
        } catch (_: Exception) {
            null
        }

        /** Real sheets are a few KB; anything this big isn't one. */
        const val MAX_SIZE = 1024 * 1024
    }
}
