package com.alexgabor.design.riso.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

/**
 * A launcher for [RisoPrintDemo] and [ComponentsDemo].
 *
 * The print effect is a `RuntimeShader`, which only runs under hardware rendering — Layoutlib does
 * not execute one, so the playground cannot be seen in a `@Preview` at all. It needs a device.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RisoTheme {
                DemoPages()
            }
        }
    }
}

private enum class Page(override val text: String) : ButtonGroupItem {
    Print("Print"),
    Components("Components"),
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
        }
    }
}
