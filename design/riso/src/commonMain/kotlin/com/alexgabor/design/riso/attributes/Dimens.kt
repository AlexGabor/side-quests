package com.alexgabor.design.riso.attributes

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


internal val LocalDimens = staticCompositionLocalOf { RisoDimens }

val RisoDimens = Dimens()

@Immutable
data class Dimens(
    val lineWidth: Dp = 2.dp,
    val iconSize: Dp = 32.dp,
    val screenPadding: Dp = 16.dp,
    val contentMaxWidth: Dp = 600.dp,
)