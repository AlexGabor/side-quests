package com.alexgabor.design.riso.attributes

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalColors = staticCompositionLocalOf { RisoColors }

val RisoColors = Colors()

@Immutable
data class Colors(
    val surface: Color = Color(0xFFFBF9F3),
    val content: Color = Color(0xFF383226),
    val accent: Color = Color(0xFFD1D2B3),
)