package com.alexgabor.bookplate.di

import androidx.compose.ui.graphics.toArgb
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.design.riso.attributes.RisoColors
import kotlin.test.Test
import kotlin.test.assertEquals

class RisoPanelInkColorsTest {

    @Test
    fun namesBecomeTheirInks() {
        val inks = RisoPanelInkColors.of(BookInks(accent = "Teal", accentVariant = "Yellow"))
        assertEquals(RisoColors.inks.teal.toArgb(), inks.accent)
        assertEquals(RisoColors.inks.yellow.toArgb(), inks.accentVariant)
        assertEquals(RisoColors.paper.toArgb(), inks.paper)
        assertEquals(RisoColors.content.toArgb(), inks.content)
    }

    @Test
    fun unknownNamesFallBackToTheThemesAccents() {
        val inks = RisoPanelInkColors.of(BookInks(accent = "Nope", accentVariant = "Neither"))
        assertEquals(RisoColors.accent.toArgb(), inks.accent)
        assertEquals(RisoColors.accentVariant.toArgb(), inks.accentVariant)
    }
}
