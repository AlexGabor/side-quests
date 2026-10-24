package com.alexgabor.design.riso.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.components.FlatButton
import com.alexgabor.design.riso.components.Icon
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.SearchField
import com.alexgabor.design.riso.components.SmallProgress
import com.alexgabor.design.riso.risograph.inks.risoInk
import kotlin.math.roundToInt

/**
 * Every icon, [SmallProgress] at its edge cases and live, and [SearchField] empty, filled and live.
 *
 * The labels are inked one by one rather than the whole list at once: a component's own pass nested
 * inside a pass that wraps a scroller stays where it was drawn when the list scrolls.
 */
@Composable
fun ComponentsDemo(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(RisoTheme.dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Heading2(modifier = Modifier.risoInk(RisoTheme.colors.content), text = "Icons") }
        item { IconChart() }

        item { Heading2(modifier = Modifier.risoInk(RisoTheme.colors.content), text = "Small progress") }
        item { ProgressSample("0 %", 0f) }
        item { ProgressSample("40 %", 0.4f) }
        item { ProgressSample("100 %", 1f) }
        item { LiveProgress() }

        item { Heading2(modifier = Modifier.risoInk(RisoTheme.colors.content), text = "Search field") }
        item {
            SearchField(
                value = "",
                onValueChange = {},
                placeholder = "Search",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SearchField(
                value = "The Left Hand of Darkness",
                onValueChange = {},
                placeholder = "Search",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { LiveSearch() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconChart() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconType.entries.forEach { type ->
            Column(
                modifier = Modifier.width(96.dp).risoInk(RisoTheme.colors.content),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(type = type, contentDescription = type.name)
                Body(text = type.name)
            }
        }
    }
}

@Composable
private fun ProgressSample(label: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Body(modifier = Modifier.risoInk(RisoTheme.colors.content), text = label)
        SmallProgress(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

/** A player-like row: the skip buttons move the line, play and pause swap. */
@Composable
private fun LiveProgress() {
    var progress by remember { mutableFloatStateOf(0.6f) }
    var playing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Body(modifier = Modifier.risoInk(RisoTheme.colors.content), text = "Live: ${(progress * 100).roundToInt()} %")
        SmallProgress(progress = progress, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlatButton(
                icon = IconType.Rewind10,
                contentDescription = "Back 10 %",
                onClick = { progress = (progress - 0.1f).coerceAtLeast(0f) },
            )
            FlatButton(
                icon = if (playing) IconType.Pause else IconType.Play,
                contentDescription = if (playing) "Pause" else "Play",
                onClick = { playing = !playing },
            )
            FlatButton(
                icon = IconType.Forward10,
                contentDescription = "Forward 10 %",
                onClick = { progress = (progress + 0.1f).coerceAtMost(1f) },
            )
        }
    }
}

@Composable
private fun LiveSearch() {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Live search",
                onSearch = { searched = it },
                modifier = Modifier.weight(1f),
            )
            FlatButton(icon = IconType.Add, contentDescription = "Add", onClick = {})
        }
        Body(modifier = Modifier.risoInk(RisoTheme.colors.content), text = searched?.let { "Searched for \"$it\"" } ?: "Press search on the keyboard")
    }
}
