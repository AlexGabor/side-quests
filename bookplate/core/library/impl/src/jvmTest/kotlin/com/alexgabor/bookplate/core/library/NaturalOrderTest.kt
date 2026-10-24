package com.alexgabor.bookplate.core.library

import kotlin.test.Test
import kotlin.test.assertEquals

class NaturalOrderTest {

    private fun sorted(vararg paths: String) = paths.sortedWith(NaturalOrder)

    @Test
    fun numbersByValue() {
        assertEquals(listOf("1.mp3", "2.mp3", "10.mp3"), sorted("10.mp3", "2.mp3", "1.mp3"))
        assertEquals(listOf("Track 9", "Track 10", "Track 100"), sorted("Track 100", "Track 10", "Track 9"))
    }

    @Test
    fun leadingZerosCountAsTheSameNumberThenFewerFirst() {
        assertEquals(listOf("1.mp3", "01.mp3", "2.mp3", "010.mp3"), sorted("010.mp3", "2.mp3", "01.mp3", "1.mp3"))
    }

    @Test
    fun textIgnoresCase() {
        assertEquals(listOf("alpha.mp3", "Beta.mp3", "gamma.mp3"), sorted("gamma.mp3", "Beta.mp3", "alpha.mp3"))
    }

    @Test
    fun foldersComparedSegmentBySegment() {
        assertEquals(
            listOf("Disc 2/01.mp3", "Disc 2/2.mp3", "Disc 10/01.mp3"),
            sorted("Disc 10/01.mp3", "Disc 2/2.mp3", "Disc 2/01.mp3"),
        )
    }

    @Test
    fun hugeNumbersDontOverflow() {
        assertEquals(
            listOf("99999999999999999999.mp3", "100000000000000000000.mp3"),
            sorted("100000000000000000000.mp3", "99999999999999999999.mp3"),
        )
    }
}
