package com.alexgabor.lib.documentaccess

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.fstat
import platform.posix.open
import platform.posix.pread
import platform.posix.stat

/**
 * Reads files reached through security-scoped bookmarks.
 *
 * A reference's root is the bookmark of the picked item, base64; a file inside a picked folder adds
 * its path relative to the folder as the child. Every use resolves the bookmark and holds the
 * folder's (or file's) security scope for as long as it needs it: for the length of a listing or
 * check, and from opening a source until it is closed.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosDocumentAccess : DocumentAccess {

    private val files = NSFileManager.defaultManager

    override suspend fun list(ref: DocumentRef): List<DocumentEntry> = withContext(CoroutineDispatchers.IO) {
        val scope = enter(ref)
        try {
            if (!ref.isFolder) {
                val name = scope.path.substringAfterLast('/')
                val attributes = files.attributesOfItemAtPath(scope.path, error = null).orEmpty()
                return@withContext listOf(
                    DocumentEntry(ref, name, name, attributes.size(), attributes.lastModified()),
                )
            }
            buildList {
                val enumerator = files.enumeratorAtPath(scope.path) ?: return@buildList
                while (true) {
                    val relative = enumerator.nextObject() as? String ?: break
                    val attributes = enumerator.fileAttributes.orEmpty()
                    val name = relative.substringAfterLast('/')
                    val isFolder = attributes[NSFileType] == NSFileTypeDirectory
                    if (name.startsWith('.')) {
                        if (isFolder) enumerator.skipDescendants()
                        continue
                    }
                    if (isFolder) continue
                    add(
                        DocumentEntry(
                            ref = DocumentRef(root = ref.root, child = join(ref.child, relative), isFolder = false),
                            relativePath = relative,
                            name = name,
                            size = attributes.size(),
                            lastModified = attributes.lastModified(),
                        ),
                    )
                }
            }.sortedBy { it.relativePath }
        } finally {
            scope.exit()
        }
    }

    override suspend fun open(ref: DocumentRef): DocumentSource = withContext(CoroutineDispatchers.IO) {
        require(!ref.isFolder) { "A folder can't be opened: $ref" }
        val scope = enter(ref)
        val descriptor = open(scope.path, O_RDONLY)
        if (descriptor < 0) {
            scope.exit()
            throw DocumentUnavailableException(Reachability.AccessLost, "Can't open $ref")
        }
        PosixSource(descriptor, scope)
    }

    override suspend fun reachability(ref: DocumentRef): Reachability = withContext(CoroutineDispatchers.IO) {
        try {
            enter(ref).exit()
            Reachability.Reachable
        } catch (e: DocumentUnavailableException) {
            e.reachability
        }
    }

    /**
     * Resolves [ref] and starts its security scope.
     *
     * A bookmark that no longer resolves means the picked item is gone. A scope the system refuses
     * means access was lost, unless the file is readable without one (for one inside the app's own
     * container). A child that isn't there, or isn't the kind the reference expects, is missing.
     *
     * @throws DocumentUnavailableException when the result is not reachable; the scope is not held then.
     */
    private fun enter(ref: DocumentRef): Scope {
        val url = Bookmarks.resolve(ref.root)
            ?: throw DocumentUnavailableException(Reachability.Missing, "The bookmark no longer resolves")
        val scoped = url.startAccessingSecurityScopedResource()
        val scope = Scope(url, scoped, path = listOfNotNull(url.path, ref.child?.takeIf { it.isNotEmpty() }).joinToString("/"))
        val reachability = memScoped {
            val isDirectory = alloc<BooleanVar>()
            when {
                !files.fileExistsAtPath(scope.path, isDirectory.ptr) -> Reachability.Missing
                isDirectory.value != ref.isFolder -> Reachability.Missing
                !scoped && !files.isReadableFileAtPath(scope.path) -> Reachability.AccessLost
                else -> Reachability.Reachable
            }
        }
        if (reachability != Reachability.Reachable) {
            scope.exit()
            throw DocumentUnavailableException(reachability, "${scope.path} is $reachability")
        }
        return scope
    }

    private class Scope(val url: NSURL, val scoped: Boolean, val path: String) {
        private var held = scoped

        fun exit() {
            if (held) url.stopAccessingSecurityScopedResource()
            held = false
        }
    }

    /** Positional reads with `pread`, holding the scope until closed. */
    private class PosixSource(private val descriptor: Int, private val scope: Scope) : DocumentSource {

        private var closed = false

        override val size: Long = memScoped {
            val info = alloc<stat>()
            if (fstat(descriptor, info.ptr) == 0) info.st_size else 0L
        }

        override fun read(position: Long, length: Int): ByteArray {
            check(!closed) { "Read after close" }
            if (position < 0 || length <= 0 || position >= size) return ByteArray(0)
            val out = ByteArray(minOf(length.toLong(), size - position).toInt())
            var filled = 0
            out.usePinned { pinned ->
                while (filled < out.size) {
                    val read = pread(
                        descriptor,
                        pinned.addressOf(filled),
                        (out.size - filled).convert(),
                        position + filled,
                    )
                    if (read <= 0) break
                    filled += read.toInt()
                }
            }
            return if (filled == out.size) out else out.copyOf(filled)
        }

        override fun close() {
            if (closed) return
            closed = true
            close(descriptor)
            scope.exit()
        }
    }
}

private fun join(parent: String?, relative: String): String =
    if (parent.isNullOrEmpty()) relative else "$parent/$relative"

private fun Map<Any?, *>.size(): Long? = (this[NSFileSize] as? NSNumber)?.longLongValue

private fun Map<Any?, *>.lastModified(): Long? =
    (this[NSFileModificationDate] as? NSDate)?.let { (it.timeIntervalSince1970 * 1000).toLong() }

/** Bookmark data as the base64 string a [DocumentRef] keeps. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object Bookmarks {

    fun create(url: NSURL): String? =
        url.bookmarkDataWithOptions(0u, includingResourceValuesForKeys = null, relativeToURL = null, error = null)
            ?.base64EncodedStringWithOptions(0u)

    /** The URL [bookmark] points at now, which follows the item if it was renamed or moved on its volume. */
    fun resolve(bookmark: String): NSURL? {
        val data = NSData.create(base64EncodedString = bookmark, options = 0u) ?: return null
        return NSURL.URLByResolvingBookmarkData(
            data,
            options = 0u,
            relativeToURL = null,
            bookmarkDataIsStale = null,
            error = null,
        )
    }
}
