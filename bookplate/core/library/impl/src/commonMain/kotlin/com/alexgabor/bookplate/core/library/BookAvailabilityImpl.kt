package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.Reachability
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Asks [documents] about each book's source, then about each of its files that isn't left out, stopping at the
 * source when it can't be reached (every file below it can't be either). Any access lost makes the book access
 * lost; otherwise any missing reference makes it missing.
 */
internal class BookAvailabilityImpl(
    private val repository: LibraryRepository,
    private val documents: DocumentAccess,
) : BookAvailability {

    private val mutableAvailability = MutableStateFlow<Map<BookId, Availability>>(emptyMap())
    override val availability: StateFlow<Map<BookId, Availability>> = mutableAvailability.asStateFlow()

    private val checkingAll = Mutex()

    override suspend fun check(id: BookId): Availability = withContext(CoroutineDispatchers.IO) {
        val details = repository.book(id).first()
        if (details == null) {
            mutableAvailability.update { it - id }
            return@withContext Availability.Available
        }
        availabilityOf(details).also { result -> mutableAvailability.update { it + (id to result) } }
    }

    override suspend fun checkAll() {
        if (!checkingAll.tryLock()) return
        try {
            withContext(CoroutineDispatchers.IO) {
                val books = repository.books().first()
                val permits = Semaphore(MaxBooksAtOnce)
                coroutineScope {
                    books.forEach { book -> launch { permits.withPermit { check(book.id) } } }
                }
            }
        } finally {
            checkingAll.unlock()
        }
    }

    private suspend fun availabilityOf(details: BookDetails): Availability {
        documents.reachability(details.book.source).toAvailability().let { if (it != Availability.Available) return it }
        var result = Availability.Available
        for (file in details.files) {
            if (file.isMissing) continue
            when (documents.reachability(file.ref)) {
                Reachability.Reachable -> Unit
                Reachability.Missing -> result = Availability.Missing
                Reachability.AccessLost -> return Availability.AccessLost
            }
        }
        return result
    }

    private fun Reachability.toAvailability(): Availability = when (this) {
        Reachability.Reachable -> Availability.Available
        Reachability.Missing -> Availability.Missing
        Reachability.AccessLost -> Availability.AccessLost
    }

    private companion object {
        /** How many books a check of the whole library looks at at once. */
        const val MaxBooksAtOnce = 4
    }
}
