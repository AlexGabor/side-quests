package com.alexgabor.bookplate.feature.player

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alexgabor.bookplate.core.player.SpeedRange
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.components.BottomSheet
import com.alexgabor.design.riso.components.track.Track
import com.alexgabor.design.riso.components.track.rememberTrackState
import com.alexgabor.design.riso.risograph.inks.risoInk
import kotlin.math.roundToInt

/** The slowest speed in tenths, where the ruler starts: its first item is 0×, but nothing below this plays. */
private val MinTenths = (SpeedRange.start * 10).roundToInt()

/**
 * A ruler from 0.3× to 3×: whole speeds are its items, tenths its subdivisions. It opens at [speed];
 * every tenth the user moves it to is sent to [onSpeedChange].
 */
@Composable
internal fun SpeedSheet(
    speed: Float,
    speedLabel: String,
    onSpeedChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val maxSpeed = SpeedRange.endInclusive.roundToInt()
    val track = rememberTrackState((0..maxSpeed).toList(), 10, rememberLazyListState(), startTick = MinTenths)
    val initialTenths = (speed * 10).roundToInt()
    val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)

    LaunchedEffect(track) { track.moveToTick(initialTenths, animate = false) }
    LaunchedEffect(track) { track.trackUserScroll() }
    LaunchedEffect(track) {
        // Only the user's own scrolls set the speed: the ruler reads 0.0× before it is first placed.
        var wasScrolling = false
        snapshotFlow { track.isUserScrolling to track.tick }.collect { (scrolling, tick) ->
            if (scrolling || wasScrolling) currentOnSpeedChange(tick / 10f)
            wasScrolling = scrolling
        }
    }

    BottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(vertical = RisoTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Heading2(
                text = "Speed $speedLabel",
                modifier = Modifier
                    .padding(horizontal = RisoTheme.dimens.screenPadding)
                    .risoInk(RisoTheme.colors.content),
            )
            Track(
                state = track,
                modifier = Modifier.fillMaxWidth().risoInk(RisoTheme.colors.content),
                showGuidelineDot = true,
                itemContent = { item, subdivision -> Body("$item.$subdivision×") },
            )
        }
    }
}

/**
 * The book's chapters under their sections' headings, the current one in accent. Tapping a chapter
 * jumps to it and closes the sheet. Opens scrolled to the current chapter.
 */
@Composable
internal fun ChaptersSheet(
    chapters: List<ChapterListItem>,
    onChapterClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val current = chapters.indexOfFirst { it is ChapterListItem.Entry && it.isCurrent }
    // One line above the current chapter, so its section heading shows too.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))

    BottomSheet(onDismissRequest = onDismissRequest) {
        val sheet = this
        Column {
            Heading2(
                text = "Chapters",
                modifier = Modifier
                    .padding(RisoTheme.dimens.screenPadding)
                    .risoInk(RisoTheme.colors.content),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = RisoTheme.dimens.screenPadding),
            ) {
                itemsIndexed(chapters) { _, item ->
                    when (item) {
                        is ChapterListItem.Section -> SectionHeading(item)
                        is ChapterListItem.Entry -> ChapterRow(item) {
                            onChapterClick(item.index)
                            sheet.dismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(section: ChapterListItem.Section) {
    Text(
        text = section.title,
        textStyle = RisoTheme.typography.heading3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = RisoTheme.dimens.screenPadding + Indent * section.depth, end = RisoTheme.dimens.screenPadding)
            .padding(top = 12.dp, bottom = 4.dp)
            .risoInk(RisoTheme.colors.content),
    )
}

/** One chapter, its press printed in the content ink across the sheet's width whatever color the line is. */
@Composable
private fun ChapterRow(chapter: ChapterListItem.Entry, onClick: () -> Unit) {
    val colors = RisoTheme.colors
    val color = if (chapter.isCurrent) colors.accent else colors.content
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .risoInk(colors.content)
                .indication(interactionSource, LocalIndication.current),
        )
        Row(
            modifier = Modifier
                .padding(start = RisoTheme.dimens.screenPadding + Indent * chapter.depth, end = RisoTheme.dimens.screenPadding)
                .padding(vertical = 12.dp)
                .risoInk(color),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = chapter.title, color = color, textStyle = RisoTheme.typography.body, modifier = Modifier.weight(1f))
            Text(text = chapter.duration, color = color, textStyle = RisoTheme.typography.body)
        }
    }
}

/** How far each enclosing section moves a line in. */
private val Indent = 16.dp
