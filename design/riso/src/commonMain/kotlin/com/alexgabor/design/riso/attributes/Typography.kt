package com.alexgabor.design.riso.attributes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.alexgabor.design.riso.FiraCode_VF
import com.alexgabor.design.riso.Res
import org.jetbrains.compose.resources.Font

@Stable
@Composable
fun firaCodeFamily() = FontFamily(
    Font(
        resource = Res.font.FiraCode_VF
    )
)

@Immutable
class Typography(
    private val fontFamily: FontFamily?,
    val body: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp
    )
)
val LocalTypography = staticCompositionLocalOf { Typography(fontFamily = null) }
