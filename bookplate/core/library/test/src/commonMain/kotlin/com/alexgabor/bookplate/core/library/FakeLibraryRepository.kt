package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [LibraryRepository] over an in-memory list, ordered and filtered like the real one. Seed it with
 * [add]; [fakeBook] builds a book with defaults for everything a test doesn't care about.
 */
class FakeLibraryRepository : LibraryRepository {

    private val library = MutableStateFlow<List<BookDetails>>(emptyList())
    private var nextId = 1L

    /** Every stored book, in the order added. */
    val all: List<BookDetails> get() = library.value

    /**
     * Stores [book] with [files] and [chapters]. A book whose id is [BookId] 0 gets the next free id.
     * Returns the stored book's id.
     */
    fun add(book: Book, files: List<AudioFile> = emptyList(), chapters: List<Chapter> = emptyList()): BookId {
        val id = if (book.id.value == 0L) BookId(nextId++) else book.id.also { nextId = maxOf(nextId, it.value + 1) }
        library.update { books -> books.filterNot { it.book.id == id } + BookDetails(book.copy(id = id), files, chapters) }
        return id
    }

    override fun books(query: String): Flow<List<Book>> {
        val needle = query.trim()
        return library.map { books ->
            books.map { it.book }
                .filter { needle.isEmpty() || it.title.contains(needle, true) || it.author?.contains(needle, true) == true }
                .sortedWith(compareByDescending<Book> { it.updatedAt }.thenByDescending { it.importedAt }.thenByDescending { it.id.value })
        }
    }

    override val lastPlayed: Flow<Book?>
        get() = library.map { books ->
            books.map { it.book }
                .filter { it.lastPlayedAt != null }
                .maxWithOrNull(compareBy<Book> { it.lastPlayedAt }.thenBy { it.id.value })
        }

    override fun book(id: BookId): Flow<BookDetails?> = library.map { books -> books.firstOrNull { it.book.id == id } }

    override suspend fun setPosition(id: BookId, position: Duration) = change(id) { copy(position = position) }

    override suspend fun setSpeed(id: BookId, speed: Float) = change(id) { copy(speed = speed) }

    override suspend fun setLastPlayed(id: BookId, at: Instant) = change(id) { copy(lastPlayedAt = at) }

    private fun change(id: BookId, block: Book.() -> Book) {
        library.update { books -> books.map { if (it.book.id == id) it.copy(book = it.book.block()) else it } }
    }
}

/** A [Book] for tests. Its id is 0, so [FakeLibraryRepository.add] gives it one. */
fun fakeBook(
    title: String,
    author: String? = null,
    narrator: String? = null,
    duration: Duration = 60.minutes,
    coverPath: String? = null,
    source: DocumentRef = DocumentRef(root = "Books/$title", child = null, isFolder = true),
    position: Duration = Duration.ZERO,
    speed: Float = 1f,
    importedAt: Instant = Instant.fromEpochMilliseconds(0),
    lastPlayedAt: Instant? = null,
    id: BookId = BookId(0),
): Book = Book(id, title, author, narrator, duration, coverPath, source, position, speed, importedAt, lastPlayedAt)
