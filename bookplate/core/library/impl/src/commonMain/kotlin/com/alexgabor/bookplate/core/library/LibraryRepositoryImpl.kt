package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class LibraryRepositoryImpl(
    private val dao: LibraryDao,
    private val covers: CoverStore,
) : LibraryRepository {

    // Filtered here rather than with LIKE: SQLite without ICU only folds ASCII case.
    override fun books(query: String): Flow<List<Book>> {
        val needle = query.trim()
        return dao.books()
            .map { rows ->
                rows.map { it.toBook() }
                    .filter { needle.isEmpty() || it.matches(needle) }
            }
            .flowOn(CoroutineDispatchers.IO)
    }

    override val lastPlayed: Flow<Book?>
        get() = dao.lastPlayed()
            .map { it?.toBook() }
            .flowOn(CoroutineDispatchers.IO)

    override fun book(id: BookId): Flow<BookDetails?> = dao.book(id.value)
        .map { it?.toDetails() }
        .flowOn(CoroutineDispatchers.IO)

    override suspend fun setPosition(id: BookId, position: Duration) = withContext(CoroutineDispatchers.IO) {
        dao.setPosition(id.value, position.inWholeMilliseconds)
    }

    override suspend fun setSpeed(id: BookId, speed: Float) = withContext(CoroutineDispatchers.IO) {
        dao.setSpeed(id.value, speed)
    }

    override suspend fun setLastPlayed(id: BookId, at: Instant) = withContext(CoroutineDispatchers.IO) {
        dao.setLastPlayed(id.value, at.toEpochMilliseconds())
    }

    override suspend fun setInks(id: BookId, inks: BookInks) = withContext(CoroutineDispatchers.IO) {
        dao.setInks(id.value, inks.accent, inks.accentVariant)
    }

    private fun Book.matches(needle: String): Boolean =
        title.contains(needle, ignoreCase = true) || author?.contains(needle, ignoreCase = true) == true

    private fun BookEntity.toBook() = Book(
        id = BookId(id),
        title = title,
        author = author,
        narrator = narrator,
        duration = durationMs.milliseconds,
        coverPath = coverFile?.let(covers::pathOf),
        source = sourceRef.toRef(),
        position = positionMs.milliseconds,
        speed = speed,
        importedAt = Instant.fromEpochMilliseconds(importedAt),
        lastPlayedAt = lastPlayedAt?.let(Instant::fromEpochMilliseconds),
        inks = BookInks(accent = inkAccent, accentVariant = inkAccentVariant),
    )

    private fun BookWithContents.toDetails(): BookDetails {
        val byParent = chapters.sortedBy { it.ordinal }.groupBy { it.parentId }
        fun level(parentId: Long?): List<Chapter> = byParent[parentId].orEmpty().map {
            Chapter(it.title, it.startMs.milliseconds, it.endMs.milliseconds, level(it.id))
        }
        return BookDetails(
            book = book.toBook(),
            files = files.sortedBy { it.ordinal }.map {
                AudioFile(
                    ref = it.sourceRef.toRef(),
                    relativePath = it.relativePath,
                    size = it.size,
                    duration = it.durationMs.milliseconds,
                    index = it.ordinal,
                    start = it.startMs.milliseconds,
                    isMissing = it.missing,
                )
            },
            chapters = level(null),
        )
    }

    private fun String.toRef(): DocumentRef = checkNotNull(DocumentRef.parse(this)) { "Not a document reference: $this" }
}
