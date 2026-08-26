package com.alexgabor.design.riso.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.Res
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.settings_icon
import org.jetbrains.compose.resources.painterResource

enum class IconType {
    Settings,
}

@Composable
fun Icon(
    type: IconType,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(type.toRes()),
        colorFilter = ColorFilter.tint(color = RisoTheme.colors.content),
        contentDescription = null,
        modifier = modifier.size(32.dp),
    )
}

private fun IconType.toRes() = when (this) {
    IconType.Settings -> Res.drawable.settings_icon
}