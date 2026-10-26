package com.alexgabor.bookplate.core.library

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** The books in the library. Flows update when the library changes. */
interface LibraryRepository {

    /**
     * Books, most recently updated first (see [Book.updatedAt]). With a non-blank [query], only books whose
     * title or author contains it, ignoring case.
     */
    fun books(query: String = ""): Flow<List<Book>>

    /** The book played most recently, or `null` when none has been played. */
    val lastPlayed: Flow<Book?>

    /** The book [id] with its files and chapters, or `null` when there is no such book. */
    fun book(id: BookId): Flow<BookDetails?>

    suspend fun setPosition(id: BookId, position: Duration)

    suspend fun setSpeed(id: BookId, speed: Float)

    suspend fun setLastPlayed(id: BookId, at: Instant)

    suspend fun setInks(id: BookId, inks: BookInks)
}
