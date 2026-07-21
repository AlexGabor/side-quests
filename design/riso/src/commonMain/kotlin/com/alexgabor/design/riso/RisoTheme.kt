package com.alexgabor.design.riso

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.alexgabor.design.riso.attributes.LocalColors
import com.alexgabor.design.riso.attributes.Colors
import com.alexgabor.design.riso.attributes.LocalTypography
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.attributes.Typography
import com.alexgabor.design.riso.attributes.firaCodeFamily

object RisoTheme {
    val colors: Colors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current
}

@Composable
fun RisoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColors provides RisoColors,
        LocalTypography provides Typography(fontFamily = firaCodeFamily()),
        LocalIndication provides ripple(), // provides the material ripple.
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = RisoColors.accent,
            backgroundColor = RisoColors.accent.copy(alpha = 0.4f)
        ), // changes the text selection background and handle color.
        content = content
    )
}