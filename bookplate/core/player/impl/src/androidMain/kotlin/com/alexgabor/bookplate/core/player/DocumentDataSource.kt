@file:OptIn(UnstableApi::class)

package com.alexgabor.bookplate.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.DocumentSource
import com.alexgabor.lib.documentaccess.DocumentUnavailableException
import com.alexgabor.lib.documentaccess.Reachability
import kotlinx.coroutines.runBlocking

/**
 * Serves ExoPlayer's reads of a [DocumentRef] through [DocumentAccess], so playback goes through the same
 * persisted access as import and never needs the platform form of a reference. URIs are made by
 * [documentUri].
 *
 * ExoPlayer opens and reads on its loader thread, so blocking on [DocumentAccess.open] is fine.
 */
internal class DocumentDataSource(private val access: DocumentAccess) : BaseDataSource(/* isNetwork = */ false) {

    private var uri: Uri? = null
    private var source: DocumentSource? = null
    private var position = 0L
    private var remaining = 0L
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val ref = dataSpec.uri.toDocumentRef()
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        val opened = try {
            runBlocking { access.open(ref) }
        } catch (e: DocumentUnavailableException) {
            val reason = when (e.reachability) {
                Reachability.AccessLost -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
                else -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            }
            throw DataSourceException(e, reason)
        }
        source = opened
        if (dataSpec.position > opened.size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, opened.size - position)
        } else {
            opened.size - position
        }
        started = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val bytes = try {
            source?.read(position, minOf(length.toLong(), remaining).toInt()) ?: return C.RESULT_END_OF_INPUT
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        bytes.copyInto(buffer, offset)
        position += bytes.size
        remaining -= bytes.size
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        val opened = source
        source = null
        try {
            opened?.close()
        } finally {
            if (started) {
                started = false
                transferEnded()
            }
        }
    }

    class Factory(private val access: DocumentAccess) : DataSource.Factory {
        override fun createDataSource(): DataSource = DocumentDataSource(access)
    }
}

private const val SCHEME = "documentref"

/** The URI [DocumentDataSource] reads [ref] from. */
internal fun documentUri(ref: DocumentRef): Uri = Uri.fromParts(SCHEME, ref.asString(), null)

private fun Uri.toDocumentRef(): DocumentRef? =
    if (scheme == SCHEME) schemeSpecificPart?.let(DocumentRef::parse) else null
