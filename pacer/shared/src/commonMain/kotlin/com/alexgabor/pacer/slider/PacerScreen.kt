package com.alexgabor.pacer.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1

@Composable
fun PacerScreen(
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Heading1(
            text = "Pacer",
            modifier = Modifier.fillMaxWidth()
                .padding(RisoTheme.dimens.screenPadding)
        )

        Body(
            text = "Tap the card you want to solve for, then scroll the other two. Pacer keeps them in sync.",
            modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PaceCalculator()
        }
    }
}

@Preview
@Composable
private fun PacerScreenPreview() {
    RisoTheme {
        PacerScreen(Modifier.background(RisoTheme.colors.paper))
    }
}