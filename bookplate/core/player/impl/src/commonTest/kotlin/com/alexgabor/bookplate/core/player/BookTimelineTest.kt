package com.alexgabor.bookplate.core.player

import com.alexgabor.bookplate.core.library.AudioFile
import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BookTimelineTest {

    /** `01 - Opening.mp3` 1:05, then `02 - Middle.mp3` 2:05: 3:10 in all. */
    private val timeline = BookTimeline(
        listOf(file(0, start = 0.seconds, duration = 65.seconds), file(1, start = 65.seconds, duration = 125.seconds)),
        duration = 190.seconds,
    )

    @Test
    fun positionInTheFirstFile() {
        assertEquals(FilePosition(0, 30.seconds), timeline.locate(30.seconds))
    }

    @Test
    fun positionInTheSecondFile() {
        assertEquals(FilePosition(1, 25.seconds), timeline.locate(90.seconds))
    }

    @Test
    fun boundaryIsTheStartOfTheLaterFile() {
        assertEquals(FilePosition(1, Duration.ZERO), timeline.locate(65.seconds))
    }

    @Test
    fun positionsOutsideTheBookAreClamped() {
        assertEquals(FilePosition(0, Duration.ZERO), timeline.locate((-5).seconds))
        assertEquals(FilePosition(1, 125.seconds), timeline.locate(500.seconds))
    }

    @Test
    fun filePositionMapsBack() {
        assertEquals(30.seconds, timeline.positionOf(FilePosition(0, 30.seconds)))
        assertEquals(90.seconds, timeline.positionOf(FilePosition(1, 25.seconds)))
    }

    @Test
    fun decoderRunningPastAFileStaysWithinItsSpan() {
        assertEquals(65.seconds, timeline.positionOf(FilePosition(0, 65.seconds + 40.milliseconds)))
        assertEquals(190.seconds, timeline.positionOf(FilePosition(1, 126.seconds)))
        assertEquals(0.seconds, timeline.positionOf(FilePosition(0, (-1).seconds)))
    }

    @Test
    fun indexOutsideThePlaylistIsAnEnd() {
        assertEquals(Duration.ZERO, timeline.positionOf(FilePosition(-1, 3.seconds)))
        assertEquals(190.seconds, timeline.positionOf(FilePosition(2, 3.seconds)))
    }

    @Test
    fun filesWithoutADurationAreLeftOut() {
        val withEmpty = BookTimeline(
            listOf(
                file(0, start = 0.seconds, duration = 65.seconds),
                file(1, start = 65.seconds, duration = Duration.ZERO),
                file(2, start = 65.seconds, duration = 125.seconds),
            ),
            duration = 190.seconds,
        )

        assertEquals(listOf(0, 2), withEmpty.files.map { it.index })
        assertEquals(FilePosition(1, 10.seconds), withEmpty.locate(75.seconds))
        assertEquals(75.seconds, withEmpty.positionOf(FilePosition(1, 10.seconds)))
    }

    @Test
    fun noFilesLocateNothing() {
        assertNull(BookTimeline(emptyList(), 60.seconds).locate(10.seconds))
    }

    private fun file(index: Int, start: Duration, duration: Duration) = AudioFile(
        ref = DocumentRef("Books/Sample", child = "$index.mp3", isFolder = false),
        relativePath = "$index.mp3",
        size = null,
        duration = duration,
        index = index,
        start = start,
    )
}
