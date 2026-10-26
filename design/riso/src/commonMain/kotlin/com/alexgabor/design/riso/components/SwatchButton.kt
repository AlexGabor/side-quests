package com.alexgabor.design.riso.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.paper.risoPaper

/**
 * A [Button] with no content, printed in [accent] and [accentVariant] instead of the theme's
 * accents, with the small shape. Shows an ink pair; the caller sizes it.
 */
@Composable
fun SwatchButton(
    accent: Color,
    accentVariant: Color,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    ButtonContainer(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        padding = PaddingValues(0.dp),
        shape = RisoTheme.shapes.smallShape,
        fill = buttonFill(accent, accentVariant),
    ) {}
}

@Preview
@Composable
private fun SwatchButtonPreview() {
    RisoTheme {
        val inks = RisoTheme.colors.inks
        Box(
            Modifier.fillMaxSize()
                .risoPaper()
                .safeDrawingPadding()
                .padding(RisoTheme.dimens.screenPadding)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SwatchButton(inks.teal, inks.yellow, onClick = {}, contentDescription = null, modifier = Modifier.size(24.dp))
                SwatchButton(inks.red, inks.blue, onClick = {}, contentDescription = null, modifier = Modifier.size(24.dp))
                SwatchButton(inks.green, inks.orange, onClick = {}, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
    }
}
