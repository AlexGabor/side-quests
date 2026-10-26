package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.AudioFile
import kotlin.time.Duration

/** A place in one of the files an [AudioOutput] plays: the file's index in its playlist and the offset into it. */
data class FilePosition(val index: Int, val offset: Duration)

/**
 * A book's files as one timeline, so a book position maps to a file and an offset and back.
 *
 * Only files with a positive duration that a partial relink hasn't left out are played: the others have no
 * place to play, but keep theirs on the timeline, so the gap they leave is skipped. [files] holds the played ones
 * in library order, and [FilePosition.index] indexes it, not the library's list. Each file starts where the
 * library says ([AudioFile.start]), so positions agree with the stored chapters.
 */
class BookTimeline(allFiles: List<AudioFile>, val duration: Duration) {

    val files: List<AudioFile> = allFiles.filter { it.duration.isPositive() && !it.isMissing }.sortedBy { it.index }

    /**
     * The file holding [position] and the offset into it. A position on a boundary is the start of the later
     * file, as is one in a gap a left-out file leaves; one past the end is the end of the last file. `null` when
     * there are no files to play.
     */
    fun locate(position: Duration): FilePosition? {
        if (files.isEmpty()) return null
        val clamped = position.coerceIn(Duration.ZERO, duration)
        var index = files.indexOfLast { it.start <= clamped }.coerceAtLeast(0)
        if (clamped >= files[index].start + files[index].duration && index < files.lastIndex) index++
        val file = files[index]
        return FilePosition(index, (clamped - file.start).coerceIn(Duration.ZERO, file.duration))
    }

    /**
     * The book position of [at]. The decoder's idea of a file's length can differ slightly from the parsed
     * one, so the result stays within the file's span: never before its start, never past the next file's
     * start or the book's end.
     */
    fun positionOf(at: FilePosition): Duration {
        val file = files.getOrNull(at.index) ?: return if (at.index < 0) Duration.ZERO else duration
        val end = files.getOrNull(at.index + 1)?.start ?: duration
        return (file.start + at.offset.coerceAtLeast(Duration.ZERO)).coerceIn(file.start, maxOf(file.start, end))
            .coerceAtMost(duration)
    }
}
