package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Instant

@JvmInline
value class BookId(val value: Long)

/**
 * A book in the library, without its files and chapters.
 *
 * @property coverPath absolute path of the cover copied into app storage, or `null` when it has none.
 * @property source the file or folder the user picked.
 * @property position where playback is in the book's timeline.
 * @property inks the pair of inks the book is printed in.
 */
data class Book(
    val id: BookId,
    val title: String,
    val author: String?,
    val narrator: String?,
    val duration: Duration,
    val coverPath: String?,
    val source: DocumentRef,
    val position: Duration,
    val speed: Float,
    val importedAt: Instant,
    val lastPlayedAt: Instant?,
    val inks: BookInks,
) {
    /** The later of [lastPlayedAt] and [importedAt]; the library is sorted by it. */
    val updatedAt: Instant
        get() = lastPlayedAt?.takeIf { it > importedAt } ?: importedAt
}

/**
 * A book's pair of inks, by their Riso names (`NamedInk.name`): the accent and the accent variant it is printed in.
 * Names, not colors, so the library stays free of Compose and a stored pair always names a real ink.
 */
data class BookInks(val accent: String, val accentVariant: String) {
    companion object {
        /** The theme's own accents: what books had before they had inks of their own. */
        val Default = BookInks(accent = "Purple", accentVariant = "Fluorescent Pink")
    }
}

/** Picks the inks a book gets: at import, at migration and as Player's next candidate. */
fun interface BookInkPicker {
    fun pick(): BookInks
}

/**
 * One audio file of a book, in playback order.
 *
 * @property relativePath below the picked folder, segments joined by `/`; for a picked file, its name.
 * @property duration zero when the file's duration couldn't be read.
 * @property index the file's place in the book, from 0.
 * @property start where the file starts in the book's timeline: the sum of the files before it.
 * @property isMissing a confirmed partial relink didn't find it: it keeps its place on the timeline but is left out
 *   of playback and of availability checks, until a relink finds it.
 */
data class AudioFile(
    val ref: DocumentRef,
    val relativePath: String,
    val size: Long?,
    val duration: Duration,
    val index: Int,
    val start: Duration,
    val isMissing: Boolean = false,
)

/**
 * A chapter, or, when it has [children], a section grouping them. [start] and [end] are in the book's
 * timeline, and a section's span its children. [title] is `null` when the source gave none.
 */
data class Chapter(
    val title: String?,
    val start: Duration,
    val end: Duration,
    val children: List<Chapter> = emptyList(),
) {
    val isSection: Boolean get() = children.isNotEmpty()
}

/** The chapters that can be played, in order: every chapter that isn't a section. */
fun List<Chapter>.playable(): List<Chapter> = flatMap { if (it.isSection) it.children.playable() else listOf(it) }

/** A book with its files and its top-level chapters. */
data class BookDetails(
    val book: Book,
    val files: List<AudioFile>,
    val chapters: List<Chapter>,
)
