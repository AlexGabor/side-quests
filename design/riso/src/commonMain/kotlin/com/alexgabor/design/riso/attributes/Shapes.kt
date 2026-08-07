package com.alexgabor.design.riso.attributes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp


internal val LocalShapes = staticCompositionLocalOf { RisoShapes }

val RisoShapes = Shapes()

@Immutable
data class Shapes(
    val standardShape: RoundedCornerShape = RoundedCornerShape(12.dp),
    val pillShape: RoundedCornerShape = RoundedCornerShape(percent = 50),
)