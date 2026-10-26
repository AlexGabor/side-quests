package com.alexgabor.design.riso

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.alexgabor.design.riso.attributes.LocalColors

/**
 * Prints [content] with [accent] and [accentVariant] in place of the theme's accents. Everything
 * else in [RisoTheme.colors] is kept. Use inks from [com.alexgabor.design.riso.attributes.Inks] so
 * they print on a loaded drum.
 */
@Composable
fun RisoInks(accent: Color, accentVariant: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColors provides LocalColors.current.copy(accent = accent, accentVariant = accentVariant),
        content = content,
    )
}
