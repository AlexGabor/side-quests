package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentAccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVPlayerActionAtItemEndAdvance
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerRateDidChangeNotification
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.actionAtItemEnd
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.defaultRate
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.resourceLoader
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [AudioOutput] over an `AVQueuePlayer`. The queue holds the current file and the next one, so playback moves
 * from one file into the next by itself; when a file ends, the one after is queued. Files are read through
 * [DocumentResourceLoader], so the player only ever sees private URLs.
 *
 * AVFoundation's state is read and changed on the main thread, so every call hops to
 * `CoroutineDispatchers.Main`, and notifications are observed on the main queue.
 *
 * The output remembers whether it was asked to play. An interruption pauses the player but not that wish, so
 * the engine shows paused and saves, and when the interruption ends with the system asking to resume, playing
 * starts again and the engine follows. Losing the output device (headphones unplugged) pauses for good.
 */
@OptIn(ExperimentalForeignApi::class)
internal class AvAudioOutput(documentAccess: DocumentAccess) : AudioOutput {

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: SharedFlow<Unit> = mutableChanges

    private val loader = DocumentResourceLoader(documentAccess)

    /** Created on the main thread by the first call, with the notification observers. */
    private val player: AVQueuePlayer by lazy { createPlayer() }

    private var files: List<OutputFile> = emptyList()

    /** Bumped on each load, so the loader refuses requests for the previous book's items. */
    private var generation = 0

    /** The items in the player's queue and the index of the file each plays, current first. */
    private val queued = mutableListOf<Pair<AVPlayerItem, Int>>()

    private var speed = 1f
    private var wantsToPlay = false
    private var isEnded = false
    private var isSessionActive = false

    /** Where the last seek goes, until it lands; `currentTime` still reads the old place until then. */
    private var pendingSeek: FilePosition? = null
    private var lastPosition = FilePosition(0, Duration.ZERO)

    override suspend fun load(files: List<OutputFile>, at: FilePosition, speed: Float) = onMain {
        player.pause()
        wantsToPlay = false
        isEnded = false
        this.files = files
        this.speed = speed
        player.defaultRate = speed
        loader.reset(++generation, files)
        queueFrom(at)
    }

    override suspend fun play() = onMain {
        activateSession()
        wantsToPlay = true
        player.defaultRate = speed
        player.play()
    }

    override suspend fun pause() = onMain {
        wantsToPlay = false
        player.pause()
    }

    override suspend fun seekTo(at: FilePosition) = onMain {
        if (at.index == currentIndex() && !isEnded) {
            seekCurrent(at)
        } else {
            isEnded = false
            queueFrom(at)
            if (wantsToPlay) player.play()
        }
    }

    override suspend fun setSpeed(speed: Float) = onMain {
        this.speed = speed
        player.defaultRate = speed
        if (player.rate != 0f) player.rate = speed
    }

    override suspend fun clear() = onMain {
        wantsToPlay = false
        isEnded = false
        player.pause()
        player.removeAllItems()
        queued.clear()
        files = emptyList()
        pendingSeek = null
        lastPosition = FilePosition(0, Duration.ZERO)
        loader.reset(++generation, emptyList())
    }

    override suspend fun status(): OutputStatus = onMain {
        val item = player.currentItem
        val index = currentIndex()
        val position = when {
            isEnded -> files.lastOrNull()?.let { FilePosition(files.lastIndex, it.duration) } ?: lastPosition
            pendingSeek != null -> pendingSeek!!
            item != null && index != null -> {
                val seconds = CMTimeGetSeconds(item.currentTime())
                if (seconds.isFinite()) FilePosition(index, seconds.seconds) else lastPosition
            }
            else -> lastPosition
        }
        lastPosition = position
        val isFailed = item?.status == AVPlayerItemStatusFailed || player.status == AVPlayerStatusFailed
        OutputStatus(
            position = position,
            isPlaying = wantsToPlay && !isEnded && !isFailed &&
                player.timeControlStatus != AVPlayerTimeControlStatusPaused,
            isEnded = isEnded,
            isFailed = isFailed,
        )
    }

    private fun currentIndex(): Int? {
        val item = player.currentItem ?: return null
        return queued.firstOrNull { it.first == item }?.second
    }

    /** Replaces the queue with the file at [at] and the one after, positioned at [at]. */
    private fun queueFrom(at: FilePosition) {
        player.removeAllItems()
        queued.clear()
        pendingSeek = null
        lastPosition = at
        if (at.index !in files.indices) return
        enqueue(at.index)
        enqueue(at.index + 1)
        loader.retain(queued.map { it.second }.toSet())
        if (at.offset.isPositive()) seekCurrent(at)
    }

    private fun enqueue(index: Int) {
        if (index !in files.indices) return
        val asset = AVURLAsset(uRL = loader.urlFor(generation, index), options = null)
        asset.resourceLoader.setDelegate(loader, queue = loader.queue)
        val item = AVPlayerItem(asset = asset)
        item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
        player.insertItem(item, afterItem = null)
        queued += item to index
    }

    private fun seekCurrent(at: FilePosition) {
        pendingSeek = at
        lastPosition = at
        val time = CMTimeMakeWithSeconds(at.offset.toDouble(kotlin.time.DurationUnit.SECONDS), preferredTimescale = 1000)
        player.seekToTime(time, toleranceBefore = CMTimeMake(0, 1), toleranceAfter = CMTimeMake(0, 1)) { _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (pendingSeek === at) pendingSeek = null
            }
        }
    }

    /** A file played to its end: the queue moved on, so queue the one after it, or the book is over. */
    private fun onItemEnded(item: AVPlayerItem) {
        val index = queued.firstOrNull { it.first == item }?.second ?: return
        queued.removeAll { it.first == item }
        if (index == files.lastIndex) {
            isEnded = true
            lastPosition = FilePosition(index, files[index].duration)
        } else {
            enqueue(index + 2)
            loader.retain(queued.map { it.second }.toSet())
        }
        changed()
    }

    private fun onInterruption(notification: NSNotification) {
        val info = notification.userInfo ?: return
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        if (type == AVAudioSessionInterruptionTypeBegan) {
            // The system has paused the player; the wish to play stays, for when the interruption ends.
            isSessionActive = false
        } else {
            val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0u
            if (wantsToPlay && options and AVAudioSessionInterruptionOptionShouldResume != 0uL) {
                activateSession()
                player.play()
            } else {
                wantsToPlay = false
            }
        }
        changed()
    }

    private fun onRouteChange(notification: NSNotification) {
        val reason = (notification.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
        if (reason != AVAudioSessionRouteChangeReasonOldDeviceUnavailable) return
        wantsToPlay = false
        player.pause()
        changed()
    }

    private fun activateSession() {
        if (isSessionActive) return
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = 0u, error = null)
        isSessionActive = session.setActive(true, error = null)
    }

    private fun changed() {
        mutableChanges.tryEmit(Unit)
    }

    private fun createPlayer(): AVQueuePlayer {
        val created = AVQueuePlayer()
        created.actionAtItemEnd = AVPlayerActionAtItemEndAdvance
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, `object` = null, queue = main) { note ->
            (note?.`object` as? AVPlayerItem)?.let(::onItemEnded)
        }
        center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, `object` = null, queue = main) { changed() }
        center.addObserverForName(AVPlayerRateDidChangeNotification, `object` = created, queue = main) { changed() }
        center.addObserverForName(AVAudioSessionInterruptionNotification, `object` = null, queue = main) { note ->
            note?.let(::onInterruption)
        }
        center.addObserverForName(AVAudioSessionRouteChangeNotification, `object` = null, queue = main) { note ->
            note?.let(::onRouteChange)
        }
        return created
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(CoroutineDispatchers.Main) { block() }
}
