package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.audiometadata.AudioMetadata
import com.alexgabor.lib.audiometadata.CueSheet
import com.alexgabor.lib.documentaccess.DocumentEntry
import kotlin.time.Duration
import com.alexgabor.lib.audiometadata.Chapter as FileChapter

/** An audio file the parser recognised. */
internal class ParsedFile(val entry: DocumentEntry, val metadata: AudioMetadata)

/** A `.cue` found in the pick, by its path relative to the picked folder. */
internal class CueFile(val relativePath: String, val sheet: CueSheet)

internal class AssembledFile(val entry: DocumentEntry, val duration: Duration, val start: Duration)

internal class AssembledBook(
    val title: String,
    val author: String?,
    val narrator: String?,
    val duration: Duration,
    val files: List<AssembledFile>,
    val chapters: List<Chapter>,
)

/**
 * Turns the parsed files of one pick into one book: a single timeline, chapters per file, sub-folders
 * as sections, and title, author and narrator from tags with name fallbacks. Pure, so it is tested
 * without storage.
 */
internal object BookAssembler {

    /**
     * @param files in playback order.
     * @param pickedName the picked file's or folder's display name.
     */
    fun assemble(pickedName: String, pickedIsFile: Boolean, files: List<ParsedFile>, cues: List<CueFile>): AssembledBook {
        var position = Duration.ZERO
        val timeline = files.map { file ->
            val duration = file.metadata.duration?.takeIf { it.isPositive() } ?: Duration.ZERO
            AssembledFile(file.entry, duration, position).also { position += duration }
        }

        val usedCues = mutableListOf<CueSheet>()
        val perFile = files.zip(timeline) { file, placed ->
            val (chapters, cue) = chaptersOf(file, placed, cues)
            cue?.let(usedCues::add)
            file to chapters
        }

        val tags = files.map { it.metadata.tags }
        val allCues = usedCues + cues.map { it.sheet }
        val title = tags.firstPresent { it.album }
            ?: tags.takeIf { pickedIsFile }?.firstPresent { it.title }
            ?: allCues.firstPresent { it.title }
            ?: if (pickedIsFile) FileKinds.stem(pickedName) else pickedName
        val author = tags.firstPresent { it.albumArtist }
            ?: tags.firstPresent { it.artist }
            ?: allCues.firstPresent { it.performer }

        return AssembledBook(
            title = title,
            author = author,
            narrator = tags.firstPresent { it.narrator },
            duration = position,
            files = timeline,
            chapters = withSections(perFile),
        )
    }

    /** Embedded chapters, else a paired cue sheet's, else one for the whole file; plus the cue used. */
    private fun chaptersOf(file: ParsedFile, placed: AssembledFile, cues: List<CueFile>): Pair<List<Chapter>, CueSheet?> {
        val embedded = file.metadata.chapters
        if (embedded.isNotEmpty()) return embedded.map { it.toChapter(placed.start) } to null

        val cue = pairedCue(file.entry, cues)
        val fromCue = cue?.chapters(file.entry.name, file.metadata.duration).orEmpty()
        if (fromCue.isNotEmpty()) return fromCue.map { it.toChapter(placed.start) } to cue

        val title = file.metadata.tags.title.present() ?: FileKinds.stem(file.entry.name)
        return listOf(Chapter(title, placed.start, placed.start + placed.duration)) to null
    }

    /** A `.cue` in the same folder, named like the audio file or naming it in a `FILE` entry. */
    private fun pairedCue(audio: DocumentEntry, cues: List<CueFile>): CueSheet? {
        val folder = audio.relativePath.folder()
        val nearby = cues.filter { it.relativePath.folder() == folder }
        val audioStem = FileKinds.stem(audio.name)
        nearby.firstOrNull { FileKinds.stem(it.relativePath.substringAfterLast('/')).equals(audioStem, ignoreCase = true) }
            ?.let { return it.sheet }
        return nearby.firstOrNull { cue ->
            cue.sheet.files.any { entry ->
                val named = entry.name?.substringAfterLast('/')?.substringAfterLast('\\')?.trim() ?: return@any false
                named.equals(audio.name, ignoreCase = true) || FileKinds.stem(named).equals(audioStem, ignoreCase = true)
            }
        }?.sheet
    }

    /**
     * Wraps the chapters of files in sub-folders into sections named after the folders. Folders every
     * file shares are skipped, so a pick holding one book folder doesn't become one big section.
     */
    private fun withSections(perFile: List<Pair<ParsedFile, List<Chapter>>>): List<Chapter> {
        val folders = perFile.map { (file, _) -> file.entry.relativePath.folder().segments() }
        val shared = folders.reduceOrNull { a, b -> a.zip(b).takeWhile { (x, y) -> x == y }.map { it.first } }.orEmpty()

        val root = Node(null)
        perFile.zip(folders) { (_, chapters), segments ->
            var node = root
            for (name in segments.drop(shared.size)) {
                val last = node.children.lastOrNull()
                node = if (last is Node && last.name == name) last else Node(name).also(node.children::add)
            }
            node.children.addAll(chapters)
        }
        return root.toChapters()
    }

    private class Node(val name: String?) {
        val children = mutableListOf<Any>()

        fun toChapters(): List<Chapter> = children.flatMap { child ->
            when (child) {
                is Chapter -> listOf(child)
                is Node -> child.toChapters().let { inside ->
                    if (inside.isEmpty()) emptyList() else listOf(Chapter(child.name, inside.first().start, inside.last().end, inside))
                }
                else -> error("Unexpected $child")
            }
        }
    }

    private fun FileChapter.toChapter(offset: Duration): Chapter = Chapter(
        title = title.present(),
        start = offset + start,
        end = offset + end,
        children = children.map { it.toChapter(offset) },
    )

    private fun String.folder(): String = substringBeforeLast('/', "")

    private fun String.segments(): List<String> = if (isEmpty()) emptyList() else split('/')

    private fun String?.present(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private inline fun <T> List<T>.firstPresent(value: (T) -> String?): String? = firstNotNullOfOrNull { value(it).present() }
}
