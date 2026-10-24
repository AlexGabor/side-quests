package com.alexgabor.lib.documentaccess

import com.alexgabor.lib.audiometadata.ByteSource
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.withContext

/**
 * Reads files and folders the user picked through a [DocumentPicker]. Never writes, moves or deletes
 * them. Every call does its work on [CoroutineDispatchers.IO].
 */
interface DocumentAccess {

    /**
     * Every file below the folder [ref], recursively, skipping hidden ones; or, for a file [ref], that
     * one file.
     *
     * @throws DocumentUnavailableException when [ref] is not reachable.
     */
    suspend fun list(ref: DocumentRef): List<DocumentEntry>

    /**
     * Opens the file [ref] for random-access reads. The caller closes it.
     *
     * @throws DocumentUnavailableException when [ref] is not reachable.
     * @throws IllegalArgumentException when [ref] is a folder.
     */
    suspend fun open(ref: DocumentRef): DocumentSource

    /** Whether [ref] can still be used, and why not. Never throws. */
    suspend fun reachability(ref: DocumentRef): Reachability
}

/** Opens [ref], runs [block] on it on [CoroutineDispatchers.IO], and closes it again. */
suspend fun <T> DocumentAccess.read(ref: DocumentRef, block: (ByteSource) -> T): T =
    withContext(CoroutineDispatchers.IO) {
        open(ref).use(block)
    }
