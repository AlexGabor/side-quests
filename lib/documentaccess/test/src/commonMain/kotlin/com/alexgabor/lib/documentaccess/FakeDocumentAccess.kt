package com.alexgabor.lib.documentaccess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.withContext

/**
 * [DocumentAccess] and [DocumentPicker] over an in-memory tree, for tests.
 *
 * Paths are `/`-separated with no leading slash, e.g. `Books/Dune/01.mp3`. Folders exist when added
 * with [addFolder] or when a file is below them. A reference only works below a granted path: picking
 * grants it, or [grant] does so directly, and [revoke] takes it away again. [delete] and [rename]
 * change the tree under live references, the way the user would in a file manager.
 *
 * Not thread safe: change the tree from the test, not concurrently with calls in flight.
 */
class FakeDocumentAccess : DocumentAccess, DocumentPicker {

    private class FakeFile(val bytes: ByteArray, val lastModified: Long)

    private val files = mutableMapOf<String, FakeFile>()
    private val folders = mutableSetOf<String>()
    private val grants = mutableSetOf<String>()
    private val picks = ArrayDeque<String?>()

    /** What each launched pick asked for, in order: `folder`, or `file:` and the MIME types. */
    val pickRequests: List<String>
        field = mutableListOf<String>()

    /** How many sources are open right now, so a test can check they were closed. */
    var openSources: Int = 0
        private set

    fun addFile(path: String, bytes: ByteArray, lastModified: Long = 0L) {
        files[normalize(path)] = FakeFile(bytes, lastModified)
    }

    fun addFolder(path: String) {
        folders += normalize(path)
    }

    /** The next pick returns [path], which must already be in the tree, and grants it. */
    fun enqueuePick(path: String) {
        picks.addLast(normalize(path))
    }

    /** The next pick is cancelled by the user. */
    fun enqueueCancel() {
        picks.addLast(null)
    }

    /** Grants [path] as if it had been picked, and returns its reference. */
    fun grant(path: String): DocumentRef {
        val normalized = normalize(path)
        val isFolder = when {
            normalized in files -> false
            folderExists(normalized) -> true
            else -> error("Nothing at $path to grant")
        }
        grants += normalized
        return DocumentRef(root = normalized, child = null, isFolder = isFolder)
    }

    /** Takes back the grant on [path]; references under it report [Reachability.AccessLost]. */
    fun revoke(path: String) {
        grants -= normalize(path)
    }

    /** Removes the file or folder at [path], with everything below it. */
    fun delete(path: String) {
        val normalized = normalize(path)
        files.keys.removeAll { it.isAtOrBelow(normalized) }
        folders.removeAll { it.isAtOrBelow(normalized) }
    }

    /** Moves the file or folder at [from], with everything below it, to [to]. */
    fun rename(from: String, to: String) {
        val source = normalize(from)
        val target = normalize(to)
        val moved = files.filterKeys { it.isAtOrBelow(source) }
        moved.keys.forEach(files::remove)
        moved.forEach { (path, file) -> files[target + path.removePrefix(source)] = file }
        val movedFolders = folders.filter { it.isAtOrBelow(source) }
        folders -= movedFolders.toSet()
        folders += movedFolders.map { target + it.removePrefix(source) }
    }

    override suspend fun list(ref: DocumentRef): List<DocumentEntry> = withContext(CoroutineDispatchers.IO) {
        requireReachable(ref)
        val path = ref.path
        if (!ref.isFolder) {
            val file = files.getValue(path)
            val name = path.substringAfterLast('/')
            return@withContext listOf(DocumentEntry(ref, name, name, file.bytes.size.toLong(), file.lastModified))
        }
        files.filterKeys { it.isAtOrBelow(path) && it != path }
            .map { (filePath, file) -> relativeTo(path, filePath) to file }
            .filter { (relative, _) -> relative.split('/').none { it.startsWith('.') } }
            .sortedBy { (relative, _) -> relative }
            .map { (relative, file) ->
                val childOfRoot = listOfNotNull(ref.child?.takeIf { it.isNotEmpty() }, relative).joinToString("/")
                DocumentEntry(
                    ref = DocumentRef(root = ref.root, child = childOfRoot, isFolder = false),
                    relativePath = relative,
                    name = relative.substringAfterLast('/'),
                    size = file.bytes.size.toLong(),
                    lastModified = file.lastModified,
                )
            }
    }

    override suspend fun open(ref: DocumentRef): DocumentSource = withContext(CoroutineDispatchers.IO) {
        require(!ref.isFolder) { "A folder can't be opened: $ref" }
        requireReachable(ref)
        openSources++
        FakeSource(files.getValue(ref.path).bytes)
    }

    override suspend fun reachability(ref: DocumentRef): Reachability = withContext(CoroutineDispatchers.IO) {
        reachabilityOf(ref)
    }

    @Composable
    override fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher {
        val callback by rememberUpdatedState(onPicked)
        return remember {
            object : DocumentPickerLauncher {
                override fun pickFile(mimeTypes: List<String>) {
                    pickRequests += "file:" + mimeTypes.joinToString(",")
                    callback(pick(folder = false))
                }

                override fun pickFolder() {
                    pickRequests += "folder"
                    callback(pick(folder = true))
                }
            }
        }
    }

    private fun pick(folder: Boolean): PickedDocument? {
        val path = picks.removeFirstOrNull() ?: return null
        val ref = grant(path)
        check(ref.isFolder == folder) { "Queued pick $path is not a ${if (folder) "folder" else "file"}" }
        return PickedDocument(ref, name = path.substringAfterLast('/'))
    }

    private fun reachabilityOf(ref: DocumentRef): Reachability {
        if (ref.root !in grants) return Reachability.AccessLost
        val exists = if (ref.isFolder) folderExists(ref.path) else ref.path in files
        return if (exists) Reachability.Reachable else Reachability.Missing
    }

    private fun requireReachable(ref: DocumentRef) {
        val reachability = reachabilityOf(ref)
        if (reachability != Reachability.Reachable) {
            throw DocumentUnavailableException(reachability, "${ref.path} is $reachability")
        }
    }

    private fun folderExists(path: String): Boolean =
        path.isEmpty() || path in folders || files.keys.any { it.startsWith("$path/") }

    private val DocumentRef.path: String
        get() = listOf(root, child.orEmpty()).filter { it.isNotEmpty() }.joinToString("/")

    private inner class FakeSource(private val bytes: ByteArray) : DocumentSource {
        private var closed = false

        override val size: Long = bytes.size.toLong()

        override fun read(position: Long, length: Int): ByteArray {
            check(!closed) { "Read after close" }
            if (position < 0 || length <= 0 || position >= size) return ByteArray(0)
            val end = minOf(size, position + length).toInt()
            return bytes.copyOfRange(position.toInt(), end)
        }

        override fun close() {
            if (!closed) openSources--
            closed = true
        }
    }
}

private fun normalize(path: String): String = path.trim('/')

private fun String.isAtOrBelow(path: String): Boolean =
    path.isEmpty() || this == path || startsWith("$path/")

private fun relativeTo(folder: String, path: String): String =
    if (folder.isEmpty()) path else path.removePrefix("$folder/")
