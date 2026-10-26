package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentEntry
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.DocumentSource
import com.alexgabor.lib.documentaccess.DocumentUnavailableException
import com.alexgabor.lib.documentaccess.Reachability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonNewDeviceAvailable
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber

/**
 * The audio session notifications the simulator can't produce (it never interrupts an app or unplugs
 * headphones), posted by hand. The player has no files here, so after `play()` it waits to play, which counts
 * as playing; the system's own pause at an interruption's start can't be reproduced, only how the output
 * reacts to the notifications.
 */
class AvAudioOutputTest {

    private val rule = TestScopeRule()
    private val output = AvAudioOutput(NoDocuments)

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    @Test
    fun headphonesUnpluggedPauses() = rule.testScope.runTest {
        startPlaying()

        post(AVAudioSessionRouteChangeNotification, AVAudioSessionRouteChangeReasonKey to AVAudioSessionRouteChangeReasonOldDeviceUnavailable)

        assertFalse(output.status().isPlaying)
    }

    @Test
    fun otherRouteChangesKeepPlaying() = rule.testScope.runTest {
        startPlaying()

        post(AVAudioSessionRouteChangeNotification, AVAudioSessionRouteChangeReasonKey to AVAudioSessionRouteChangeReasonNewDeviceAvailable)

        assertTrue(output.status().isPlaying)
    }

    @Test
    fun interruptionEndingWithResumePlaysAgain() = rule.testScope.runTest {
        startPlaying()

        post(AVAudioSessionInterruptionNotification, AVAudioSessionInterruptionTypeKey to AVAudioSessionInterruptionTypeBegan)
        post(
            AVAudioSessionInterruptionNotification,
            AVAudioSessionInterruptionTypeKey to AVAudioSessionInterruptionTypeEnded,
            AVAudioSessionInterruptionOptionKey to AVAudioSessionInterruptionOptionShouldResume,
        )

        assertTrue(output.status().isPlaying)
    }

    @Test
    fun interruptionEndingWithoutResumeStaysPaused() = rule.testScope.runTest {
        startPlaying()

        post(AVAudioSessionInterruptionNotification, AVAudioSessionInterruptionTypeKey to AVAudioSessionInterruptionTypeBegan)
        post(AVAudioSessionInterruptionNotification, AVAudioSessionInterruptionTypeKey to AVAudioSessionInterruptionTypeEnded)

        assertFalse(output.status().isPlaying)
    }

    private suspend fun startPlaying() {
        output.load(emptyList(), FilePosition(0, Duration.ZERO), 1f)
        output.play()
        assertTrue(output.status().isPlaying)
    }

    private fun post(name: String?, vararg info: Pair<String?, ULong>) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            name!!,
            `object` = null,
            userInfo = info.associate { (key, value) -> key to NSNumber(unsignedLong = value) },
        )
    }
}

private object NoDocuments : DocumentAccess {
    override suspend fun list(ref: DocumentRef): List<DocumentEntry> = emptyList()

    override suspend fun open(ref: DocumentRef): DocumentSource =
        throw DocumentUnavailableException(Reachability.Missing, "No documents in this test")

    override suspend fun reachability(ref: DocumentRef): Reachability = Reachability.Missing
}
