package com.alexgabor.design.riso.components.track

import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackStateTest {

    /**
     * The case this whole refactor exists for: a track carrying a scroll offset restored from a
     * previous instance of the screen, which has not been laid out yet and so has no pixel grid to
     * divide that offset by. It used to divide by zero, and `Infinity.toInt()` is [Int.MAX_VALUE].
     */
    @Test
    fun anUnmeasuredTrackReadsZeroRatherThanGarbage() {
        val track = TrackState((0..100).toList(), subdivisions = 5, LazyListState(3, 40))

        assertEquals(15, track.tick)
        assertEquals(3, track.selectedItem)
        assertEquals(0, track.selectedSubdivision)
    }

    @Test
    fun aMeasuredTrackReadsTheSubdivisionUnderTheGuideline() {
        val track = TrackState((0..100).toList(), subdivisions = 5, LazyListState(3, 40))
        track.itemSizePx = 100f

        // 40px into an item whose five subdivisions are 20px apart.
        assertEquals(17, track.tick)
        assertEquals(3, track.selectedItem)
        assertEquals(2, track.selectedSubdivision)
    }

    /** An offset of a whole item belongs to the next item, never to a sixth subdivision of this one. */
    @Test
    fun aSubdivisionCannotSpillIntoTheNextItem() {
        val track = TrackState((0..100).toList(), subdivisions = 5, LazyListState(3, 100))
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
        assertEquals(100, TrackState((0..20).toList(), subdivisions = 5, LazyListState()).maxTick)
        assertEquals(59, TrackState((0..59).toList(), subdivisions = 1, LazyListState()).maxTick)
    }

    /** A ruler of minutes for something 3:15 long, in 10-second lines: it ends halfway past 3:10. */
    @Test
    fun aRulerCanEndInsideItsLastItem() {
        val track = TrackState((0..3).toList(), subdivisions = 6, LazyListState(), endTick = 19.5f)

        assertEquals(19.5f, track.end)
        assertEquals(19, track.maxTick)
        assertEquals(6f, track.ticksDrawnIn(2))
        assertEquals(1.5f, track.ticksDrawnIn(3))
        // The end padding makes up for the 4.5 lines after the end.
        assertEquals(45f, track.tailPx(subdivisionPx = 10f))
    }

    @Test
    fun withoutAnEndTheRulerEndsAtTheLastItem() {
        val track = TrackState((0..3).toList(), subdivisions = 6, LazyListState())

        assertEquals(18f, track.end)
        assertEquals(18, track.maxTick)
        assertEquals(0f, track.ticksDrawnIn(3))
        assertEquals(60f, track.tailPx(subdivisionPx = 10f))
    }

    /** An end outside the last item is kept inside it. */
    @Test
    fun anEndIsKeptInsideTheLastItem() {
        assertEquals(18f, TrackState((0..3).toList(), subdivisions = 6, LazyListState(), endTick = 2f).end)
        assertEquals(23, TrackState((0..3).toList(), subdivisions = 6, LazyListState(), endTick = 40f).maxTick)
    }

    /** A speed ruler of whole speeds in tenths that starts at 0.3×. */
    @Test
    fun aRulerCanStartInsideItsFirstItem() {
        val track = TrackState((0..3).toList(), subdivisions = 10, LazyListState(), startTick = 3)
        track.itemSizePx = 100f

        assertEquals(3, track.start)
        assertEquals(3, track.ticksSkippedIn(0))
        assertEquals(0, track.ticksSkippedIn(1))
        // At 0.0× it may not go back at all; at 0.5× it may go back 20px.
        assertEquals(0f, track.backwardScrollLeftPx())
        assertEquals(-20f, TrackState((0..3).toList(), 10, LazyListState(0, 50), startTick = 3)
            .apply { itemSizePx = 100f }.backwardScrollLeftPx())
    }

    /** A start past the last reachable line is kept before it. */
    @Test
    fun aStartIsKeptBeforeTheEnd() {
        assertEquals(30, TrackState((0..3).toList(), subdivisions = 10, LazyListState(), startTick = 99).start)
        assertEquals(0, TrackState((0..3).toList(), subdivisions = 10, LazyListState(), startTick = -4).start)
    }
}
