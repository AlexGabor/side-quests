package com.alexgabor.lib.documentaccess

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.withContext

/**
 * Reads SAF documents.
 *
 * A reference's root is the picked URI: a tree for a folder, a document for a single file. A file
 * listed inside a tree keeps its own document URI, built against the tree, as the child, so opening it
 * needs no walk from the root. The persisted grant is always the root's.
 */
internal class AndroidDocumentAccess(private val resolver: ContentResolver) : DocumentAccess {

    override suspend fun list(ref: DocumentRef): List<DocumentEntry> = withContext(CoroutineDispatchers.IO) {
        requireReachable(ref)
        if (!ref.isFolder) return@withContext listOf(single(ref))
        val tree = Uri.parse(ref.root)
        val start = ref.child?.let { DocumentsContract.getDocumentId(Uri.parse(it)) }
            ?: DocumentsContract.getTreeDocumentId(tree)
        buildList { walk(tree, start, prefix = "", into = this) }
    }

    override suspend fun open(ref: DocumentRef): DocumentSource = withContext(CoroutineDispatchers.IO) {
        require(!ref.isFolder) { "A folder can't be opened: $ref" }
        requireReachable(ref)
        val descriptor = resolver.openFileDescriptor(ref.documentUri, "r")
            ?: throw DocumentUnavailableException(Reachability.Missing, "No file for $ref")
        DescriptorSource(descriptor)
    }

    override suspend fun reachability(ref: DocumentRef): Reachability = withContext(CoroutineDispatchers.IO) {
        reachabilityOf(ref)
    }

    private fun reachabilityOf(ref: DocumentRef): Reachability {
        val root = Uri.parse(ref.root)
        val granted = resolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission }
        if (!granted) return Reachability.AccessLost
        return try {
            // Providers disagree on how a gone document fails: no row, FileNotFoundException,
            // IllegalArgumentException… Only a SecurityException means access, the rest mean missing.
            val found = resolver.query(ref.documentUri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
            if (found) Reachability.Reachable else Reachability.Missing
        } catch (_: SecurityException) {
            Reachability.AccessLost
        } catch (_: Exception) {
            Reachability.Missing
        }
    }

    private fun requireReachable(ref: DocumentRef) {
        val reachability = reachabilityOf(ref)
        if (reachability != Reachability.Reachable) {
            throw DocumentUnavailableException(reachability, "$ref is $reachability")
        }
    }

    /** One `DocumentsContract` query per folder, breadth by breadth, rather than a `DocumentFile` per child. */
    private fun walk(tree: Uri, documentId: String, prefix: String, into: MutableList<DocumentEntry>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val folders = mutableListOf<Pair<String, String>>()
        resolver.query(children, COLUMNS, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                if (name.startsWith('.')) continue
                val path = prefix + name
                if (cursor.getString(2) == Document.MIME_TYPE_DIR) {
                    folders += id to "$path/"
                    continue
                }
                into += DocumentEntry(
                    ref = DocumentRef(
                        root = tree.toString(),
                        child = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        isFolder = false,
                    ),
                    relativePath = path,
                    name = name,
                    size = cursor.longOrNull(3),
                    lastModified = cursor.longOrNull(4),
                )
            }
        }
        folders.forEach { (id, path) -> walk(tree, id, path, into) }
    }

    private fun single(ref: DocumentRef): DocumentEntry {
        val columns = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        return resolver.query(ref.documentUri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else {
                val name = cursor.getString(0).orEmpty()
                DocumentEntry(ref, name, name, cursor.longOrNull(1), cursor.longOrNull(2))
            }
        } ?: throw DocumentUnavailableException(Reachability.Missing, "No file for $ref")
    }

    /** The document a reference points at: the child, the picked file, or the picked tree's root. */
    private val DocumentRef.documentUri: Uri
        get() {
            child?.let { return Uri.parse(it) }
            val root = Uri.parse(root)
            return if (isFolder) {
                DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
            } else {
                root
            }
        }

    private companion object {
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}

private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

/** Positional reads on a file descriptor, so nothing depends on a stream's position. */
private class DescriptorSource(private val descriptor: ParcelFileDescriptor) : DocumentSource {

    private val stream = FileInputStream(descriptor.fileDescriptor)
    private val channel: FileChannel = stream.channel

    override val size: Long = descriptor.statSize.takeIf { it >= 0 } ?: channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || length <= 0 || position >= size) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - position).toInt())
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) break
        }
        return if (buffer.hasRemaining()) buffer.array().copyOf(buffer.position()) else buffer.array()
    }

    override fun close() {
        stream.close()
        descriptor.close()
    }
}
