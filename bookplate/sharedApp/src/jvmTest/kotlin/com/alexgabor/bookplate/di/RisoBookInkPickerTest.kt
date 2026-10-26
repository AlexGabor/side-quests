package com.alexgabor.bookplate.di

import com.alexgabor.design.riso.attributes.RisoColors
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RisoBookInkPickerTest {

    @Test
    fun picksTwoDifferentAccentInks() {
        val picker = RisoBookInkPicker(Random(7))
        val accents = RisoColors.inks.accents.map { it.name }.toSet()

        repeat(200) {
            val inks = picker.pick()
            assertNotEquals(inks.accent, inks.accentVariant)
            assertTrue(inks.accent in accents && inks.accentVariant in accents, inks.toString())
        }
        assertTrue("Black" !in accents)
    }
}
