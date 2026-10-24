package com.alexgabor.lib.documentaccess

import com.alexgabor.lib.audiometadata.ByteSource

/** What the user picked: a reference to keep, and the name the platform shows for it. */
data class PickedDocument(val ref: DocumentRef, val name: String)

/**
 * One file found by [DocumentAccess.list].
 *
 * @property relativePath the path below the listed folder, segments joined by `/`; for a listed file,
 *   its name.
 * @property size in bytes, when the platform knows it.
 * @property lastModified in epoch milliseconds, when the platform knows it.
 */
data class DocumentEntry(
    val ref: DocumentRef,
    val relativePath: String,
    val name: String,
    val size: Long?,
    val lastModified: Long?,
)

/** Whether a [DocumentRef] can still be used. */
enum class Reachability {
    Reachable,

    /** Deleted, or moved or renamed away from where the reference points. */
    Missing,

    /** It may still be there, but the platform no longer lets the app at it. */
    AccessLost,
}

/** Thrown when a reference to list or open is not [Reachability.Reachable]. */
class DocumentUnavailableException(
    val reachability: Reachability,
    message: String,
) : Exception(message)

/** An opened file. Reads block, as for any [ByteSource]; close it when done. */
interface DocumentSource : ByteSource, AutoCloseable
