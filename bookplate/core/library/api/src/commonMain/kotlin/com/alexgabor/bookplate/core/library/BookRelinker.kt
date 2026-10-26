package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.PickedDocument

/**
 * Points a book at a new place of its files. The files are matched by relative path, then by name, then by size and
 * duration; only the references change, never the position, speed, chapters, cover or timestamps. The user's files
 * are only listed and read.
 */
interface BookRelinker {

    /**
     * Matches book [id]'s files against [pick] and saves the match, unless some files aren't found: then it returns
     * [RelinkResult.NeedsConfirmation] and changes nothing, unless every one of them is in [confirmMissing] (relative
     * paths, as that result names them). Files not found are then left out of the book until a relink finds them.
     */
    suspend fun relink(id: BookId, pick: PickedDocument, confirmMissing: Set<String> = emptySet()): RelinkResult
}

sealed interface RelinkResult {

    /** The book points at the pick now; [leftOut] are the relative paths of its files that are left out. */
    data class Relinked(val leftOut: List<String>) : RelinkResult

    /** The book was available and already where the pick is: nothing was written. */
    data object Unchanged : RelinkResult

    /** Only [found] of [total] files are in the pick; [missing] are the others. Nothing was changed. */
    data class NeedsConfirmation(val found: Int, val total: Int, val missing: List<String>) : RelinkResult

    /** None of the book's files are in the pick. Nothing was changed. */
    data object NoMatch : RelinkResult

    /** A folder was needed and a file picked ([needsFolder]), or the reverse. Nothing was changed. */
    data class WrongKind(val needsFolder: Boolean) : RelinkResult

    /** The pick is already book [bookId]'s source. Nothing was changed. */
    data class OtherBook(val bookId: BookId) : RelinkResult

    /** The pick couldn't be listed. Nothing was changed. */
    data class Failed(val reason: FailureReason, val message: String? = null) : RelinkResult

    data object NoSuchBook : RelinkResult
}
