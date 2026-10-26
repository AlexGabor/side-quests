package com.alexgabor.bookplate.di

import androidx.compose.ui.graphics.toArgb
import com.alexgabor.bookplate.core.library.BookInks
import com.alexgabor.bookplate.core.player.PanelInkColors
import com.alexgabor.bookplate.core.player.PanelInks
import com.alexgabor.design.riso.attributes.RisoColors

/** A book's ink names as the Riso inks they stand for; a name Riso doesn't know prints in the theme's accents. */
internal object RisoPanelInkColors : PanelInkColors {
    override fun of(inks: BookInks): PanelInks {
        val colors = RisoColors
        return PanelInks(
            paper = colors.paper.toArgb(),
            content = colors.content.toArgb(),
            accent = (colors.inks.named(inks.accent)?.color ?: colors.accent).toArgb(),
            accentVariant = (colors.inks.named(inks.accentVariant)?.color ?: colors.accentVariant).toArgb(),
        )
    }
}
