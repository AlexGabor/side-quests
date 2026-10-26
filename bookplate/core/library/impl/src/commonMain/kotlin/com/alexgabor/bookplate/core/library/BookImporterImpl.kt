package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.audiometadata.AudioMetadataReader
import com.alexgabor.lib.audiometadata.ByteSource
import com.alexgabor.lib.audiometadata.CueSheet
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentEntry
import com.alexgabor.lib.documentaccess.DocumentUnavailableException
import com.alexgabor.lib.documentaccess.PickedDocument
import com.alexgabor.lib.documentaccess.Reachability
import com.alexgabor.lib.documentaccess.read
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.withContext

/**
 * Lists, reads and assembles a pick into one book, then saves it in one transaction. The user's files
 * are only listed and read; the cover is copied into app storage.
 */
internal class BookImporterImpl(
    private val dao: LibraryDao,
    private val covers: CoverStore,
    private val documents: DocumentAccess,
    private val clock: Clock,
    private val inks: BookInkPicker,
) : BookImporter {

    override suspend fun import(document: PickedDocument, onProgress: (ImportProgress) -> Unit): ImportResult =
        withContext(CoroutineDispatchers.IO) { importOnIo(document, onProgress) }

    private suspend fun importOnIo(document: PickedDocument, onProgress: (ImportProgress) -> Unit): ImportResult {
        val sourceRef = document.ref.asString()
        dao.idBySource(sourceRef)?.let { return ImportResult.AlreadyInLibrary(BookId(it)) }

        onProgress(ImportProgress.Listing)
        val entries = try {
            documents.list(document.ref)
        } catch (e: DocumentUnavailableException) {
            return ImportResult.Failed(e.reachability.toSourceFailure(), e.message)
        }

        val audio = entries.filter { FileKinds.isAudio(it.name) }.sortedWith(compareBy(NaturalOrder) { it.relativePath })
        if (audio.isEmpty()) return ImportResult.NoAudioFound
        val fingerprint = Fingerprint.of(document.name, audio.map { it.relativePath to it.size })
        dao.idByFingerprint(fingerprint)?.let { return ImportResult.AlreadyInLibrary(BookId(it)) }

        val parsed = mutableListOf<ParsedFile>()
        onProgress(ImportProgress.Reading(0, audio.size))
        audio.forEachIndexed { i, entry ->
            val metadata = try {
                documents.read(entry.ref) { AudioMetadataReader.read(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentUnavailableException) {
                val reason = if (e.reachability == Reachability.AccessLost) FailureReason.AccessLost else FailureReason.Unreadable
                return ImportResult.Failed(reason, "${entry.relativePath}: ${e.message}")
            } catch (e: Exception) {
                return ImportResult.Failed(FailureReason.Unreadable, "${entry.relativePath}: ${e.message}")
            }
            // Named like audio but not audio this app reads: ignored like any other file.
            if (metadata != null) parsed += ParsedFile(entry, metadata)
            onProgress(ImportProgress.Reading(i + 1, audio.size))
        }
        if (parsed.isEmpty()) return ImportResult.NoAudioFound

        val book = BookAssembler.assemble(
            pickedName = document.name,
            pickedIsFile = !document.ref.isFolder,
            files = parsed,
            cues = readCues(entries, parsed),
        )

        onProgress(ImportProgress.Saving)
        val coverFile = saveCover(parsed, entries)
        val bookInks = inks.pick()
        val row = BookEntity(
            sourceRef = sourceRef,
            fingerprint = fingerprint,
            title = book.title,
            author = book.author,
            narrator = book.narrator,
            durationMs = book.duration.inWholeMilliseconds,
            coverFile = coverFile,
            importedAt = clock.now().toEpochMilliseconds(),
            inkAccent = bookInks.accent,
            inkAccentVariant = bookInks.accentVariant,
        )
        val files = book.files.mapIndexed { i, file ->
            AudioFileEntity(
                bookId = 0,
                ordinal = i,
                sourceRef = file.entry.ref.asString(),
                relativePath = file.entry.relativePath,
                size = file.entry.size,
                durationMs = file.duration.inWholeMilliseconds,
                startMs = file.start.inWholeMilliseconds,
            )
        }
        val id = try {
            dao.insert(row, files, book.chapters)
        } catch (e: Throwable) {
            coverFile?.let(covers::delete)
            if (e is CancellationException) throw e
            // Lost a race with the same pick imported concurrently.
            dao.idBySource(sourceRef)?.let { return ImportResult.AlreadyInLibrary(BookId(it)) }
            throw e
        }
        return ImportResult.Success(BookId(id))
    }

    /** Sheets in folders holding an audio file that has no chapters of its own. Unreadable ones are skipped. */
    private suspend fun readCues(entries: List<DocumentEntry>, parsed: List<ParsedFile>): List<CueFile> {
        val folders = parsed.filter { it.metadata.chapters.isEmpty() }.map { it.entry.relativePath.folder() }.toSet()
        return entries
            .filter { FileKinds.isCueSheet(it.name) && it.relativePath.folder() in folders }
            .mapNotNull { entry ->
                readOrNull(entry) { CueSheet.read(it) }?.let { CueFile(entry.relativePath, it) }
            }
    }

    /**
     * The first embedded picture in file order, else `cover.*` / `folder.*` at the top of the pick or in
     * the folder all audio shares. Returns the stored file's name, or `null` when there is none.
     */
    private suspend fun saveCover(parsed: List<ParsedFile>, entries: List<DocumentEntry>): String? {
        parsed.firstNotNullOfOrNull { it.metadata.cover }?.let { cover ->
            return writeCover(cover.data, FileKinds.imageExtension(cover.mimeType))
        }
        val shared = parsed.map { it.entry.relativePath.folder() }.reduce(::sharedFolder)
        val image = entries
            .filter { it.relativePath.folder().let { folder -> folder.isEmpty() || folder == shared } }
            .mapNotNull { entry -> FileKinds.folderImageRank(entry.name)?.let { entry to it } }
            .minWithOrNull(compareBy({ it.second }, { it.first.relativePath.count { c -> c == '/' } }))
            ?.first
            ?: return null
        if ((image.size ?: 0) > MAX_COVER_SIZE) return null
        val bytes = readOrNull(image) { it.readAll() } ?: return null
        return writeCover(bytes, FileKinds.extension(image.name))
    }

    private fun writeCover(bytes: ByteArray, extension: String): String? = try {
        covers.write(bytes, extension)
    } catch (_: Exception) {
        // A book without a cover is still a book.
        null
    }

    private suspend fun <T> readOrNull(entry: DocumentEntry, block: (ByteSource) -> T?): T? = try {
        documents.read(entry.ref, block)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun ByteSource.readAll(): ByteArray? {
        if (size > MAX_COVER_SIZE) return null
        val out = ByteArray(size.toInt())
        var position = 0
        while (position < out.size) {
            val chunk = read(position.toLong(), out.size - position)
            if (chunk.isEmpty()) return null
            chunk.copyInto(out, position)
            position += chunk.size
        }
        return out
    }

    private fun Reachability.toSourceFailure(): FailureReason =
        if (this == Reachability.AccessLost) FailureReason.AccessLost else FailureReason.SourceMissing

    private fun String.folder(): String = substringBeforeLast('/', "")

    private fun sharedFolder(a: String, b: String): String {
        val left = if (a.isEmpty()) emptyList() else a.split('/')
        val right = if (b.isEmpty()) emptyList() else b.split('/')
        return left.zip(right).takeWhile { (x, y) -> x == y }.joinToString("/") { it.first }
    }

    private companion object {
        const val MAX_COVER_SIZE = 20L * 1024 * 1024
    }
}
