package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.audiometadata.AudioMetadataReader
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentEntry
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.DocumentUnavailableException
import com.alexgabor.lib.documentaccess.PickedDocument
import com.alexgabor.lib.documentaccess.Reachability
import com.alexgabor.lib.documentaccess.read
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withContext

/**
 * Lists the pick, matches it with [RelinkMatcher] and saves the new references in one transaction. Only the book's
 * source and fingerprint and its files' references, paths, sizes and left-out marks are written; the user's files are
 * only listed and read.
 */
internal class BookRelinkerImpl(
    private val dao: LibraryDao,
    private val documents: DocumentAccess,
    private val availability: BookAvailability,
) : BookRelinker {

    override suspend fun relink(id: BookId, pick: PickedDocument, confirmMissing: Set<String>): RelinkResult =
        withContext(CoroutineDispatchers.IO) { relinkOnIo(id, pick, confirmMissing) }

    private suspend fun relinkOnIo(id: BookId, pick: PickedDocument, confirmMissing: Set<String>): RelinkResult {
        val current = dao.bookNow(id.value) ?: return RelinkResult.NoSuchBook
        val source = checkNotNull(DocumentRef.parse(current.book.sourceRef)) { "Not a reference: ${current.book.sourceRef}" }
        if (source.isFolder != pick.ref.isFolder) return RelinkResult.WrongKind(needsFolder = source.isFolder)

        val sourceRef = pick.ref.asString()
        dao.idBySource(sourceRef)?.takeIf { it != id.value }?.let { return RelinkResult.OtherBook(BookId(it)) }

        val entries = try {
            documents.list(pick.ref)
        } catch (e: DocumentUnavailableException) {
            val reason = if (e.reachability == Reachability.AccessLost) FailureReason.AccessLost else FailureReason.SourceMissing
            return RelinkResult.Failed(reason, e.message)
        }
        val audio = entries.filter { FileKinds.isAudio(it.name) }.sortedWith(compareBy(NaturalOrder) { it.relativePath })
        val fingerprint = Fingerprint.of(pick.name, audio.map { it.relativePath to it.size })
        dao.idsByFingerprint(fingerprint).firstOrNull { it != id.value }?.let { return RelinkResult.OtherBook(BookId(it)) }

        val rows = current.files.sortedBy { it.ordinal }
        val stored = rows.map { StoredFile(it.ordinal, it.relativePath, it.size, it.durationMs.milliseconds) }
        // A picked file is the candidate whatever it is named; in a folder, only what import would have read.
        val candidates = if (pick.ref.isFolder) audio else entries
        val matching = RelinkMatcher.match(stored, candidates, ::durationOf)
        if (matching.links.isEmpty()) return RelinkResult.NoMatch

        val leftOutBefore = rows.filter { it.missing }.map { it.relativePath }.toSet()
        val newlyMissing = matching.unmatched.map { it.relativePath }.filter { it !in leftOutBefore }
        if (!confirmMissing.containsAll(newlyMissing)) {
            return RelinkResult.NeedsConfirmation(found = matching.links.size, total = stored.size, missing = newlyMissing)
        }

        val updated = rows.map { row ->
            val (entry, _) = matching.links[row.ordinal] ?: return@map row.copy(missing = true)
            row.copy(sourceRef = entry.ref.asString(), relativePath = entry.relativePath, size = entry.size, missing = false)
        }
        val leftOut = updated.filter { it.missing }.map { it.relativePath }
        val changed = updated != rows || sourceRef != current.book.sourceRef || fingerprint != current.book.fingerprint
        if (!changed) {
            // Re-picking where an unavailable book already points (say, to grant access again) fixes it without a write.
            val wasAvailable = availability.availability.value.of(id) == Availability.Available
            availability.check(id)
            return if (wasAvailable) RelinkResult.Unchanged else RelinkResult.Relinked(leftOut)
        }
        try {
            dao.relink(id.value, sourceRef, fingerprint, updated)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Lost a race with an import of the same pick.
            dao.idBySource(sourceRef)?.takeIf { it != id.value }?.let { return RelinkResult.OtherBook(BookId(it)) }
            throw e
        }
        availability.check(id)
        return RelinkResult.Relinked(leftOut)
    }

    private suspend fun durationOf(entry: DocumentEntry): Duration? = try {
        documents.read(entry.ref) { AudioMetadataReader.read(it)?.duration }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
