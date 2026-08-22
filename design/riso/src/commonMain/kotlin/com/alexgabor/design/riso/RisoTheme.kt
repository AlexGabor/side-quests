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
import com.alexgabor.design.riso.attributes.Dimens
import com.alexgabor.design.riso.attributes.LocalDimens
import com.alexgabor.design.riso.attributes.LocalPress
import com.alexgabor.design.riso.attributes.LocalShapes
import com.alexgabor.design.riso.attributes.LocalTypography
import com.alexgabor.design.riso.attributes.Press
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.attributes.RisoDimens
import com.alexgabor.design.riso.attributes.RisoPress
import com.alexgabor.design.riso.attributes.RisoShapes
import com.alexgabor.design.riso.attributes.Shapes
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

    val dimens: Dimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current

    val press: Press
        @Composable
        @ReadOnlyComposable
        get() = LocalPress.current
}

@Composable
fun RisoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColors provides RisoColors,
        LocalDimens provides RisoDimens,
        LocalPress provides RisoPress,
        LocalShapes provides RisoShapes,
        LocalTypography provides Typography(fontFamily = firaCodeFamily()),
        LocalIndication provides ripple(), // provides the material ripple.
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = RisoColors.accent,
            backgroundColor = RisoColors.accent.copy(alpha = 0.3f)
        ), // changes the text selection background and handle color.
        content = content
    )
}