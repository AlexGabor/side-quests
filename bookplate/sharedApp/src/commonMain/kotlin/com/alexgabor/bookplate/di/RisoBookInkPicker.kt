package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.library.BookInkPicker
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.design.riso.attributes.RisoColors
import kotlin.random.Random

/** Two different inks at random from Riso's accent inks, so every pair prints on loaded drums. */
internal class RisoBookInkPicker(private val random: Random = Random.Default) : BookInkPicker {
    override fun pick(): BookInks {
        val (accent, accentVariant) = RisoColors.inks.accents.shuffled(random).take(2)
        return BookInks(accent = accent.name, accentVariant = accentVariant.name)
    }
}
