package com.alexgabor.design.riso.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.risograph.RisoPrintDemo
import com.alexgabor.design.riso.risograph.paper.risoPaper

/** [RisoPrintDemo], [ComponentsDemo] and [SheetsDemo], one page at a time. */
@Composable
fun RisoDemo() {
    RisoTheme {
        DemoPages()
    }
}

private enum class Page(override val text: String) : ButtonGroupItem {
    Print("Print"),
    Components("Components"),
    Sheets("Sheets"),
}

@Composable
private fun DemoPages() {
    var page by rememberSaveable { mutableStateOf(Page.Print) }

    Column(
        modifier = Modifier.fillMaxSize().risoPaper().safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonGroup(
            selected = page,
            *Page.entries.toTypedArray(),
            modifier = Modifier.padding(RisoTheme.dimens.screenPadding),
            onSelect = { page = it },
        )
        when (page) {
            Page.Print -> RisoPrintDemo()
            Page.Components -> ComponentsDemo(Modifier.fillMaxSize())
            Page.Sheets -> SheetsDemo(Modifier.fillMaxSize())
        }
    }
}
