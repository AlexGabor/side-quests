package com.alexgabor.bookplate.core.library

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class LibraryDao {

    @Query(
        "SELECT * FROM books " +
            "ORDER BY MAX(imported_at, IFNULL(last_played_at, 0)) DESC, imported_at DESC, id DESC",
    )
    abstract fun books(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC, id DESC LIMIT 1")
    abstract fun lastPlayed(): Flow<BookEntity?>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    abstract fun book(id: Long): Flow<BookWithContents?>

    @Query("SELECT id FROM books WHERE source_ref = :sourceRef")
    abstract suspend fun idBySource(sourceRef: String): Long?

    @Query("SELECT id FROM books WHERE fingerprint = :fingerprint ORDER BY id LIMIT 1")
    abstract suspend fun idByFingerprint(fingerprint: String): Long?

    @Query("UPDATE books SET position_ms = :positionMs WHERE id = :id")
    abstract suspend fun setPosition(id: Long, positionMs: Long)

    @Query("UPDATE books SET speed = :speed WHERE id = :id")
    abstract suspend fun setSpeed(id: Long, speed: Float)

    @Query("UPDATE books SET last_played_at = :at WHERE id = :id")
    abstract suspend fun setLastPlayed(id: Long, at: Long)

    /** Saves a whole book at once: the row, its files and its chapter tree. Returns the book's id. */
    @Transaction
    open suspend fun insert(book: BookEntity, files: List<AudioFileEntity>, chapters: List<Chapter>): Long {
        val bookId = insertBook(book)
        insertFiles(files.map { it.copy(bookId = bookId) })
        var ordinal = 0
        suspend fun insertAll(level: List<Chapter>, parentId: Long?) {
            for (chapter in level) {
                val id = insertChapter(
                    ChapterEntity(
                        bookId = bookId,
                        parentId = parentId,
                        ordinal = ordinal++,
                        title = chapter.title,
                        startMs = chapter.start.inWholeMilliseconds,
                        endMs = chapter.end.inWholeMilliseconds,
                    ),
                )
                insertAll(chapter.children, id)
            }
        }
        insertAll(chapters, parentId = null)
        return bookId
    }

    @Insert
    protected abstract suspend fun insertBook(book: BookEntity): Long

    @Insert
    protected abstract suspend fun insertFiles(files: List<AudioFileEntity>)

    @Insert
    protected abstract suspend fun insertChapter(chapter: ChapterEntity): Long
}
