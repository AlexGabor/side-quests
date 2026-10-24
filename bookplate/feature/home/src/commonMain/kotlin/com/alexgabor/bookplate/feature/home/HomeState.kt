package com.alexgabor.bookplate.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.alexgabor.bookplate.core.library.Book
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookImporter
import com.alexgabor.bookplate.core.library.FailureReason
import com.alexgabor.bookplate.core.library.ImportProgress
import com.alexgabor.bookplate.core.library.ImportResult
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** What the library part of Home shows. */
sealed interface Library {
    data object Loading : Library

    /** No books at all, and no search. */
    data object Empty : Library

    /** A search that matches no book. */
    data class NoResults(val query: String) : Library

    data class Books(val rows: List<BookRow>) : Library
}

/** One book as its row shows it. [progress] is the fraction played, for `SmallProgress`. */
data class BookRow(
    val id: BookId,
    val title: String,
    val author: String,
    val duration: String,
    val progress: Float,
    val percent: String,
    val coverPath: String?,
)

/** The import Home is running, or the result of the last one. */
sealed interface ImportStatus {
    /** The picked file or folder's name. */
    val name: String

    /** [fraction] is set while files are being read. */
    data class Running(override val name: String, val step: String, val fraction: Float?) : ImportStatus

    data class Finished(override val name: String, val message: String, val isError: Boolean) : ImportStatus
}

/**
 * Home: the library filtered by [query], the add-book choice and the import it starts.
 *
 * Only the query and whether the add sheet is open are saved; the library is read back from
 * [repository], and an import does not outlive [scope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeState(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val importer: BookImporter,
    query: String = "",
    addSheetOpen: Boolean = false,
) {

    // A flow rather than snapshotFlow over [query], so the holder needs no Compose snapshot observer.
    private val queries = MutableStateFlow(query)

    val query: State<String>
        field = mutableStateOf(query)

    val library: State<Library>
        field = mutableStateOf<Library>(Library.Loading)

    val addSheetOpen: State<Boolean>
        field = mutableStateOf(addSheetOpen)

    val importStatus: State<ImportStatus?>
        field = mutableStateOf<ImportStatus?>(null)

    /** Tells a late progress report from an earlier import apart from the current one. */
    private var attempt = 0

    init {
        scope.launch {
            queries
                .flatMapLatest { query -> repository.books(query).map { books -> libraryOf(query, books) } }
                .collect { library.value = it }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        queries.value = value
    }

    fun onAddClick() {
        addSheetOpen.value = true
    }

    fun onAddSheetDismissed() {
        addSheetOpen.value = false
    }

    /** Imports [pick] as a book. A cancelled pick, or one made while another import runs, is ignored. */
    fun onPicked(pick: PickedDocument?) {
        if (pick == null || importStatus.value is ImportStatus.Running) return
        val current = ++attempt
        importStatus.value = ImportStatus.Running(pick.name, LookingForAudio, fraction = null)
        scope.launch {
            val result = try {
                importer.import(pick) { progress ->
                    // Reported from the importer's IO dispatcher: state is only written from [scope].
                    scope.launch {
                        if (current == attempt && importStatus.value is ImportStatus.Running) {
                            importStatus.value = running(pick.name, progress)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportResult.Failed(FailureReason.Unreadable, e.message)
            }
            importStatus.value = finished(pick, result)
        }
    }

    /** Closes a finished import's result. A running import can't be closed. */
    fun onStatusClosed() {
        if (importStatus.value is ImportStatus.Finished) importStatus.value = null
    }

    private fun running(name: String, progress: ImportProgress): ImportStatus.Running = when (progress) {
        ImportProgress.Listing -> ImportStatus.Running(name, LookingForAudio, fraction = null)
        is ImportProgress.Reading -> ImportStatus.Running(
            name,
            "Reading file ${progress.done} of ${progress.total}",
            fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else null,
        )
        ImportProgress.Saving -> ImportStatus.Running(name, "Saving…", fraction = 1f)
    }

    private suspend fun finished(pick: PickedDocument, result: ImportResult): ImportStatus.Finished = when (result) {
        is ImportResult.Success ->
            ImportStatus.Finished(pick.name, "Added “${titleOf(result.bookId, pick)}”", isError = false)
        is ImportResult.AlreadyInLibrary ->
            ImportStatus.Finished(pick.name, "“${titleOf(result.bookId, pick)}” is already in your library", isError = false)
        ImportResult.NoAudioFound ->
            ImportStatus.Finished(pick.name, "No audio found in “${pick.name}”", isError = true)
        is ImportResult.Failed ->
            ImportStatus.Finished(pick.name, "Couldn't add “${pick.name}”. ${result.reason.explanation}", isError = true)
    }

    private suspend fun titleOf(id: BookId, pick: PickedDocument): String =
        repository.book(id).first()?.book?.title ?: pick.name

    private companion object {
        const val LookingForAudio = "Looking for audio…"
    }
}

private val FailureReason.explanation: String
    get() = when (this) {
        FailureReason.SourceMissing -> "The file or folder is gone."
        FailureReason.AccessLost -> "Bookplate can no longer open it."
        FailureReason.Unreadable -> "A file couldn't be read."
    }

internal fun libraryOf(query: String, books: List<Book>): Library = when {
    books.isNotEmpty() -> Library.Books(books.map { it.toRow() })
    query.isBlank() -> Library.Empty
    else -> Library.NoResults(query.trim())
}

internal fun Book.toRow(): BookRow {
    val fraction = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    return BookRow(
        id = id,
        title = title,
        author = author ?: "Unknown author",
        duration = formatDuration(duration),
        progress = fraction,
        // Rounded down, so a book only reads 100% once it is finished.
        percent = "${(fraction * 100).toInt()}%",
        coverPath = coverPath,
    )
}

/** `m:ss` under an hour, `h:mm:ss` from an hour. */
internal fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "$hours:${minutes.pad()}:${seconds.pad()}" else "$minutes:${seconds.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')

internal fun homeStateSaver(
    scope: CoroutineScope,
    repository: LibraryRepository,
    importer: BookImporter,
): Saver<HomeState, Any> = listSaver(
    save = { state -> listOf(state.query.value, state.addSheetOpen.value) },
    restore = { saved ->
        HomeState(scope, repository, importer, query = saved[0] as String, addSheetOpen = saved[1] as Boolean)
    },
)

@Composable
fun rememberHomeState(
    repository: LibraryRepository = koinInject(),
    importer: BookImporter = koinInject(),
): HomeState {
    val scope = rememberCoroutineScope()
    val saver = remember(scope, repository, importer) { homeStateSaver(scope, repository, importer) }
    return rememberSaveable(scope, repository, importer, saver = saver) {
        HomeState(scope, repository, importer)
    }
}
