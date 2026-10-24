package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.PickedDocument

/**
 * [BookImporter] that returns queued results. [enqueueBook] queues a success that adds a book to
 * [repository]; [enqueue] queues any other result, which leaves the library as it is.
 */
class FakeBookImporter(private val repository: FakeLibraryRepository) : BookImporter {

    private sealed interface Queued {
        val progress: List<ImportProgress>

        class Result(val result: ImportResult, override val progress: List<ImportProgress>) : Queued
        class AddBook(
            val book: Book,
            val files: List<AudioFile>,
            val chapters: List<Chapter>,
            override val progress: List<ImportProgress>,
        ) : Queued
    }

    private val queue = ArrayDeque<Queued>()

    /** Every document passed to [import], in order. */
    val imported: List<PickedDocument>
        field = mutableListOf<PickedDocument>()

    fun enqueue(result: ImportResult, progress: List<ImportProgress> = emptyList()) {
        require(result !is ImportResult.Success) { "Use enqueueBook for a success, so the book exists" }
        queue.addLast(Queued.Result(result, progress))
    }

    fun enqueueBook(
        book: Book,
        files: List<AudioFile> = emptyList(),
        chapters: List<Chapter> = emptyList(),
        progress: List<ImportProgress> = emptyList(),
    ) {
        queue.addLast(Queued.AddBook(book, files, chapters, progress))
    }

    override suspend fun import(document: PickedDocument, onProgress: (ImportProgress) -> Unit): ImportResult {
        imported += document
        val next = queue.removeFirstOrNull() ?: error("No import result queued for ${document.name}")
        next.progress.forEach(onProgress)
        return when (next) {
            is Queued.Result -> next.result
            is Queued.AddBook -> ImportResult.Success(repository.add(next.book, next.files, next.chapters))
        }
    }
}
