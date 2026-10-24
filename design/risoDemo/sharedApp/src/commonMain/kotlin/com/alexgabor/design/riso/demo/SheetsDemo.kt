package com.alexgabor.design.riso.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.components.BottomSheet
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.SmallProgress
import com.alexgabor.design.riso.components.track.Track
import com.alexgabor.design.riso.components.track.rememberTrackState
import com.alexgabor.design.riso.risograph.inks.risoInk

private enum class Sheet { Speed, Chapters }

private val chapters = (1..40).map { Chapter("Chapter $it", minutes = 9 + (it * 7) % 23) }

private data class Chapter(val title: String, val minutes: Int)

/**
 * [BottomSheet] over an inked page: a short one holding a [Track], like a speed control, and a long
 * one holding a list, like a book's chapters. The page keeps a press counter, which stays put while
 * a sheet is open: nothing behind a sheet can be pressed.
 */
@Composable
fun SheetsDemo(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<Sheet?>(null) }
    var speedTenths by remember { mutableIntStateOf(10) }
    var chapter by remember { mutableIntStateOf(0) }
    var presses by remember { mutableIntStateOf(0) }
    val ink = Modifier.risoInk(RisoTheme.colors.content)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(RisoTheme.dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading2(modifier = ink, text = "Bottom sheet")
        Body(
            modifier = ink,
            text = "No scrim: the page behind a sheet is printed lighter, so its dots shrink and the " +
                "paper shows between them. Drag a sheet down, press back or tap above it to close it.",
        )
        Body(modifier = ink, text = "Speed ${speedLabel(speedTenths)} · ${chapters[chapter].title}")
        SmallProgress(progress = (chapter + 1f) / chapters.size, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(text = "Speed", onClick = { open = Sheet.Speed })
            Button(text = "Chapters", onClick = { open = Sheet.Chapters })
        }
        Button(text = "Pressed $presses", onClick = { presses++ })
    }

    when (open) {
        Sheet.Speed -> BottomSheet(onDismissRequest = { open = null }) {
            SpeedSheet(speedTenths) { speedTenths = it }
        }
        Sheet.Chapters -> BottomSheet(onDismissRequest = { open = null }) {
            LazyColumn(contentPadding = PaddingValues(bottom = RisoTheme.dimens.screenPadding)) {
                items(chapters.withIndex().toList(), key = { it.index }) { (index, item) ->
                    ChapterRow(item, current = index == chapter) {
                        chapter = index
                        dismiss()
                    }
                }
            }
        }
        null -> Unit
    }
}

/** 0.3× to 3×, in tenths: whole speeds are the track's items, tenths its subdivisions. */
@Composable
private fun SpeedSheet(tenths: Int, onChange: (Int) -> Unit) {
    val state = rememberTrackState((0..3).toList(), 10, rememberLazyListState())
    LaunchedEffect(state) { state.moveToTick(tenths, animate = false) }
    LaunchedEffect(state.tick) {
        val clamped = state.tick.coerceAtLeast(3)
        if (clamped != state.tick) state.moveToTick(clamped, animate = true)
        onChange(clamped)
    }

    Column(
        modifier = Modifier.padding(vertical = RisoTheme.dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Heading2(
            modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding).risoInk(RisoTheme.colors.content),
            text = "Speed ${speedLabel(tenths)}",
        )
        Track(
            state = state,
            modifier = Modifier.fillMaxWidth().risoInk(RisoTheme.colors.content),
            showGuidelineDot = true,
            itemContent = { item, subdivision -> Body("$item.$subdivision×") },
        )
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, current: Boolean, onClick: () -> Unit) {
    val colors = RisoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RisoTheme.dimens.screenPadding, vertical = 12.dp)
            .risoInk(if (current) colors.accent else colors.content),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val color = if (current) colors.accent else colors.content
        Text(text = chapter.title, color = color, textStyle = RisoTheme.typography.body, modifier = Modifier.weight(1f))
        Text(text = "${chapter.minutes} min", color = color, textStyle = RisoTheme.typography.body)
    }
}

private fun speedLabel(tenths: Int): String = "${tenths / 10}.${tenths % 10}×"
