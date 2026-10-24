package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.PickedDocument

/** Turns a picked file or folder into one book in the library. Only ever reads the user's files. */
interface BookImporter {

    /**
     * Imports [document] as one book. [onProgress] is called as files are listed and read. Only
     * [ImportResult.Success] changes the library.
     */
    suspend fun import(document: PickedDocument, onProgress: (ImportProgress) -> Unit = {}): ImportResult
}

sealed interface ImportProgress {
    data object Listing : ImportProgress

    /** [done] of [total] audio files have been read. */
    data class Reading(val done: Int, val total: Int) : ImportProgress

    data object Saving : ImportProgress
}

sealed interface ImportResult {
    data class Success(val bookId: BookId) : ImportResult

    /** The same file or folder is already a book; nothing was changed. */
    data class AlreadyInLibrary(val bookId: BookId) : ImportResult

    /** Nothing in the pick is audio this app can read. */
    data object NoAudioFound : ImportResult

    data class Failed(val reason: FailureReason, val message: String? = null) : ImportResult
}

enum class FailureReason {
    /** The picked file or folder is gone. */
    SourceMissing,

    /** The platform no longer lets the app at the picked file or folder. */
    AccessLost,

    /** A file could be listed but not read. */
    Unreadable,
}
