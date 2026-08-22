package com.alexgabor.pacer.track

import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackSateTest {

    /**
     * The case this whole refactor exists for: a track carrying a scroll offset restored from a
     * previous instance of the screen, which has not been laid out yet and so has no pixel grid to
     * divide that offset by. It used to divide by zero, and `Infinity.toInt()` is [Int.MAX_VALUE].
     */
    @Test
    fun anUnmeasuredTrackReadsZeroRatherThanGarbage() {
        val track = TrackSate((0..100).toList(), subdivisions = 5, LazyListState(3, 40))

        assertEquals(15, track.tick)
        assertEquals(3, track.selectedItem)
        assertEquals(0, track.selectedSubdivision)
    }

    @Test
    fun aMeasuredTrackReadsTheSubdivisionUnderTheGuideline() {
        val track = TrackSate((0..100).toList(), subdivisions = 5, LazyListState(3, 40))
        track.itemSizePx = 100f

        // 40px into an item whose five subdivisions are 20px apart.
        assertEquals(17, track.tick)
        assertEquals(3, track.selectedItem)
        assertEquals(2, track.selectedSubdivision)
    }

    /** An offset of a whole item belongs to the next item, never to a sixth subdivision of this one. */
    @Test
    fun aSubdivisionCannotSpillIntoTheNextItem() {
        val track = TrackSate((0..100).toList(), subdivisions = 5, LazyListState(3, 100))
        track.itemSizePx = 100f

        assertEquals(4, track.selectedSubdivision)
        assertEquals(19, track.tick)
    }

    /**
     * The last item's trailing subdivisions can't be scrolled onto the guideline: the end padding
     * stops at its leading edge.
     */
    @Test
    fun theLastReachableLineIsTheStartOfTheLastItem() {
        assertEquals(100, TrackSate((0..20).toList(), subdivisions = 5, LazyListState()).maxTick)
        assertEquals(59, TrackSate((0..59).toList(), subdivisions = 1, LazyListState()).maxTick)
    }
}
