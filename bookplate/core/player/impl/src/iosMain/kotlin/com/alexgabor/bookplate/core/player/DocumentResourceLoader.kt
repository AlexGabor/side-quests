package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.audiometadata.AudioFormat
import com.alexgabor.lib.audiometadata.AudioMetadataReader
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.AVAssetResourceLoader
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorFileDoesNotExist
import platform.Foundation.create
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_queue_create

/**
 * Feeds AVFoundation the bytes of a book's files through [DocumentAccess], the way Android's
 * `DocumentDataSource` does: the player never sees a file's location, only [urlFor]'s private URLs.
 *
 * Opening a file through `DocumentAccess` starts its security-scoped access, which lasts until the source is
 * closed. Sources open when AVFoundation first asks for a file in the [window] and are closed when the file
 * leaves it, or on the next [reset]. Requests for an older generation or a file outside the window fail.
 *
 * AVFoundation calls in on [queue]; each request is then answered on a global queue, in [ChunkSize] pieces,
 * stopping when it is cancelled, so a request made after a seek isn't stuck behind one asking for the rest of
 * the file.
 */
@OptIn(ExperimentalForeignApi::class)
internal class DocumentResourceLoader(private val documentAccess: DocumentAccess) :
    NSObject(), AVAssetResourceLoaderDelegateProtocol {

    val queue = dispatch_queue_create("com.alexgabor.bookplate.player.loader", null)

    private val lock = NSLock()
    private var generation = 0
    private var files: List<OutputFile> = emptyList()
    private var window: Set<Int> = emptySet()
    private val open = mutableMapOf<Int, OpenFile>()

    private class OpenFile(val source: DocumentSource, val contentType: String?)

    /** Starts serving [files] as generation [generation], releasing everything from before. */
    fun reset(generation: Int, files: List<OutputFile>) = locked {
        this.generation = generation
        this.files = files
        window = emptySet()
        closeAllExcept(emptySet())
    }

    /** Only the files at [indices] may be read from now on; the others are released. */
    fun retain(indices: Set<Int>) = locked {
        window = indices
        closeAllExcept(indices)
    }

    fun urlFor(generation: Int, index: Int): NSURL = NSURL(string = "$Scheme://g$generation/$index")

    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val request = shouldWaitForLoadingOfRequestedResource
        val url = request.request.URL ?: return false
        if (url.scheme != Scheme) return false
        val requestGeneration = url.host?.removePrefix("g")?.toIntOrNull()
        val index = url.lastPathComponent?.toIntOrNull()
        if (requestGeneration == null || index == null) return false
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0u)) {
            serve(request, requestGeneration, index)
        }
        return true
    }

    private fun serve(request: AVAssetResourceLoadingRequest, requestGeneration: Int, index: Int) {
        val file = try {
            fileFor(requestGeneration, index)
        } catch (e: Exception) {
            null
        }
        if (file == null) {
            request.finishLoadingWithError(unavailable())
            return
        }
        val size = file.source.size
        request.contentInformationRequest?.let { info ->
            info.contentType = file.contentType
            info.contentLength = size
            info.byteRangeAccessSupported = true
        }
        val data = request.dataRequest
        if (data == null) {
            request.finishLoading()
            return
        }
        var offset = data.requestedOffset
        val end = if (data.requestsAllDataToEndOfResource) size else minOf(size, offset + data.requestedLength)
        while (offset < end && !request.isCancelled()) {
            val bytes = try {
                file.source.read(offset, minOf(ChunkSize.toLong(), end - offset).toInt())
            } catch (e: Exception) {
                // Closed under us: the file left the window.
                request.finishLoadingWithError(unavailable())
                return
            }
            if (bytes.isEmpty()) break
            data.respondWithData(bytes.toNSData())
            offset += bytes.size
        }
        if (!request.isCancelled()) request.finishLoading()
    }

    /** The open file at [index], opening it if it's in the window; `null` when it may not be read. */
    private fun fileFor(requestGeneration: Int, index: Int): OpenFile? = locked {
        if (requestGeneration != generation || index !in window) return@locked null
        open[index]?.let { return@locked it }
        val ref = files.getOrNull(index)?.ref ?: return@locked null
        val source = runBlocking { documentAccess.open(ref) }
        val format = try {
            AudioMetadataReader.detect(source)
        } catch (e: Exception) {
            null
        }
        OpenFile(source, format?.uti()).also { open[index] = it }
    }

    private fun closeAllExcept(keep: Set<Int>) {
        val iterator = open.entries.iterator()
        while (iterator.hasNext()) {
            val (index, file) = iterator.next()
            if (index in keep) continue
            file.source.close()
            iterator.remove()
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

}

private const val Scheme = "bookplate-doc"

private const val ChunkSize = 256 * 1024

private fun unavailable(): NSError = NSError.errorWithDomain(NSURLErrorDomain, NSURLErrorFileDoesNotExist, null)

/** The type identifier AVFoundation needs to pick a parser, from the format the bytes say. */
private fun AudioFormat.uti(): String = when (this) {
    AudioFormat.Mp3 -> "public.mp3"
    AudioFormat.Mp4 -> "public.mpeg-4-audio"
    AudioFormat.Aac -> "public.aac-audio"
    AudioFormat.Wav -> "com.microsoft.waveform-audio"
    AudioFormat.Flac -> "org.xiph.flac"
    AudioFormat.Vorbis, AudioFormat.Opus -> "org.xiph.ogg-audio"
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}
