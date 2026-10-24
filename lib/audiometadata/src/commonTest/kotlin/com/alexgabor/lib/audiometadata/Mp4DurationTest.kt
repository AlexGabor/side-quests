package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Mp4DurationTest {

    private fun duration(vararg moov: ByteArray) = readMp4(Mp4.ftyp() + Mp4.atom("moov", *moov)).duration

    @Test
    fun mvhdVersion0() {
        assertEquals(3.seconds, duration(Mp4.mvhd(1000, 3000)))
    }

    @Test
    fun mvhdVersion1() {
        // 30 hours at 44.1 kHz is more than 32 bits hold.
        val samples = 30 * 3600 * 44100L
        assertEquals(30.hours, duration(Mp4.mvhd(44100, samples, version = 1)))
    }

    @Test
    fun fractionalSeconds() {
        assertEquals(1500.milliseconds, duration(Mp4.mvhd(8000, 12000)))
    }

    @Test
    fun fallsBackToMdhd() {
        assertEquals(2.seconds, duration(Mp4.mvhd(1000, 0), Mp4.trak(1, "soun", 8000, 16000, headerDuration = 0)))
        assertEquals(2.seconds, duration(Mp4.trak(1, "soun", 8000, 16000, version = 1)))
    }

    @Test
    fun soundTrackIsUsedNotTheTextTrack() {
        assertEquals(
            2.seconds,
            duration(Mp4.mvhd(1000, 0), Mp4.trak(2, "text", 1000, 9000), Mp4.trak(1, "soun", 8000, 16000)),
        )
    }

    @Test
    fun fallsBackToTkhdInTheMovieTimescale() {
        assertEquals(2.seconds, duration(Mp4.mvhd(600, 0), Mp4.trak(1, "soun", headerDuration = 1200, mdhd = false)))
        assertEquals(2.seconds, duration(Mp4.mvhd(600, 0), Mp4.trak(1, "soun", headerDuration = 1200, mdhd = false, version = 1)))
    }

    @Test
    fun allOnesIsUnknown() {
        assertNull(duration(Mp4.mvhd(1000, 0xFFFFFFFFL)))
        assertNull(duration(Mp4.mvhd(1000, -1L, version = 1)))
        assertNull(duration(Mp4.mvhd(0, 3000)))
    }
}
