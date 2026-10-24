package com.alexgabor.bookplate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.components.FlatButton
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.Toolbar
import com.alexgabor.design.riso.risograph.inks.risoInk

// Placeholder Player screen for the app skeleton. It is replaced by `feature/player` (M10), at which
// point this file goes.

@Composable
internal fun PlayerPlaceholder(
    bookId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(RisoTheme.dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(RisoTheme.dimens.screenPadding),
    ) {
        Toolbar(
            modifier = Modifier.risoInk(RisoTheme.colors.content).fillMaxWidth(),
            startContent = {
                FlatButton(icon = IconType.Back, onClick = onBackClick, contentDescription = "Back")
            },
        ) {
            Heading1(text = "Player")
        }
        Body(text = "Book $bookId", modifier = Modifier.risoInk(RisoTheme.colors.content))
    }
}
