package com.alexgabor.bookplate.core.library

import kotlinx.coroutines.flow.StateFlow

/** Whether a book's files can be reached. */
enum class Availability {
    Available,

    /** A file or the picked folder is gone: deleted, or moved or renamed away. */
    Missing,

    /** The platform no longer lets the app at the files. */
    AccessLost,
}

/**
 * Checks whether books' files can still be reached, and holds the last answer per book. Nothing is stored: a book
 * that hasn't been checked since the app started has no entry and counts as available.
 */
interface BookAvailability {

    /** The last check of each book that has been checked. */
    val availability: StateFlow<Map<BookId, Availability>>

    /**
     * Checks book [id]'s source and every file a partial relink hasn't left out, publishes and returns the result.
     * A book that isn't in the library is [Availability.Available], and has no entry. Never throws.
     */
    suspend fun check(id: BookId): Availability

    /**
     * Checks every book in the library, a few at a time, publishing each result as it is known. Returns when all
     * are checked; does nothing while another check of every book runs.
     */
    suspend fun checkAll()
}

/** [id]'s last known availability: [Availability.Available] until it has been checked. */
fun Map<BookId, Availability>.of(id: BookId): Availability = this[id] ?: Availability.Available
