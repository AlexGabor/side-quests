package com.alexgabor.bookplate.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.FlatButton
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.Toolbar
import com.alexgabor.design.riso.components.track.Track
import com.alexgabor.design.riso.components.track.rememberTrackState
import com.alexgabor.design.riso.layout.contentWidth
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Bookplate's Player for book [bookId]: what is playing, where, and the controls, with the speed and
 * chapters sheets.
 */
@Composable
fun PlayerScreen(
    bookId: BookId,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: PlayerScreenState = rememberPlayerScreenState(bookId),
) {
    val ui by state.ui
    val sheet by state.sheet

    PlayerContent(
        ui = ui,
        onBackClick = onBackClick,
        onChaptersClick = state::onChaptersClick,
        onSpeedClick = state::onSpeedClick,
        onPlayPause = state::onPlayPause,
        onSkipBack = state::onSkipBack,
        onSkipForward = state::onSkipForward,
        onScrub = state::onScrub,
        onScrubEnd = state::onScrubEnd,
        modifier = modifier,
    )

    val shown = ui ?: return
    when (sheet) {
        PlayerSheet.Speed -> SpeedSheet(
            speed = shown.speed,
            speedLabel = shown.speedLabel,
            onSpeedChange = state::onSpeedChange,
            onDismissRequest = state::onSheetDismissed,
        )
        PlayerSheet.Chapters -> ChaptersSheet(
            chapters = shown.chapters,
            onChapterClick = state::onChapterClick,
            onDismissRequest = state::onSheetDismissed,
        )
        null -> Unit
    }
}

@Composable
private fun PlayerContent(
    ui: PlayerUi?,
    onBackClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onScrub: (Duration) -> Unit,
    onScrubEnd: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = RisoTheme.dimens.screenPadding
    val ink = Modifier.risoInk(RisoTheme.colors.content)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .contentWidth(available = maxWidth, max = RisoTheme.dimens.contentMaxWidth)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Toolbar(
                modifier = ink.fillMaxWidth(),
                startContent = {
                    FlatButton(icon = IconType.Back, contentDescription = "Back", onClick = onBackClick)
                },
                endContent = {
                    if (ui != null) {
                        Row {
                            FlatButton(icon = IconType.Chapters, contentDescription = "Chapters", onClick = onChaptersClick)
                            FlatButton(icon = IconType.Speed, contentDescription = "Speed", onClick = onSpeedClick)
                        }
                    }
                },
            ) {}
            if (ui == null) return@Column

            Spacer(Modifier.height(padding))
            Text(
                text = ui.title,
                textStyle = RisoTheme.typography.heading2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = ink,
            )
            Text(
                text = ui.author,
                textStyle = RisoTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = ink.padding(top = 4.dp),
            )

            // Whatever height is left over: a square as large as fits, so a small screen shrinks the
            // cover rather than pushing the controls off.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = padding),
                contentAlignment = Alignment.Center,
            ) {
                BookCover(
                    coverPath = ui.coverPath,
                    title = ui.title,
                    modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }

            ui.chapterTitle?.let { chapter ->
                Text(
                    text = chapter,
                    textStyle = RisoTheme.typography.heading3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = ink.padding(bottom = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Body(text = ui.positionLabel, modifier = ink.weight(1f))
                Body(text = ui.durationLabel, modifier = ink)
            }
            PositionTrack(
                position = ui.position,
                duration = ui.duration,
                isPlaying = ui.isPlaying,
                speed = ui.speed,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
                modifier = Modifier.bleed(padding).then(ink).fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatButton(icon = IconType.Rewind10, contentDescription = "Rewind 10 seconds", onClick = onSkipBack)
                Button(
                    icon = if (ui.isPlaying) IconType.Pause else IconType.Play,
                    contentDescription = if (ui.isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                )
                FlatButton(icon = IconType.Forward10, contentDescription = "Forward 10 seconds", onClick = onSkipForward)
            }
        }
    }
}

/** Lets this child reach [horizontal] past each side of the space its parent gives it. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val side = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + side * 2,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + side * 2 else constraints.maxWidth,
        ),
    )
    val width = (placeable.width - side * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-side, 0) }
}

/** The ruler's grid: one item a minute, one subdivision every [TickLength]. */
private val TickLength = 10.seconds
private const val TicksPerMinute = 6

/**
 * The position as a `Track` with its guideline in the middle. While nobody drags it, it follows
 * [position]: gliding at [speed] while playing, from one reported position to the next, so it doesn't
 * step a ruler line at a time. A drag reports where it is through [onScrub], and [onScrubEnd] once it
 * has come to rest.
 */
@Composable
private fun PositionTrack(
    position: Duration,
    duration: Duration,
    isPlaying: Boolean,
    speed: Float,
    onScrub: (Duration) -> Unit,
    onScrubEnd: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whole minutes up to the one the book ends in, and the ruler ends at the book's exact end.
    val minutes = floor(duration / 1.minutes).toInt().coerceAtLeast(0)
    val items = remember(minutes) { (0..minutes).toList() }
    val endTick = (duration / TickLength).toFloat()
    val track = rememberTrackState(items, TicksPerMinute, rememberLazyListState(), endTick = endTick)
    val playback by rememberUpdatedState(Playback(position, duration, isPlaying, speed))
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    LaunchedEffect(track) { track.trackUserScroll() }
    LaunchedEffect(track) {
        var placed = false
        var shown = 0f
        snapshotFlow { track.isUserScrolling to playback }.collectLatest { (scrolling, playback) ->
            if (scrolling) return@collectLatest
            if (!playback.isPlaying) {
                shown = playback.ticksAt(Duration.ZERO)
                track.moveTo(shown, animate = placed)
                placed = true
                return@collectLatest
            }
            // Runs until the next report, which restarts it from the reported position. It moves a
            // pixel at a time rather than every frame: as smooth to the eye, and the app stays idle in
            // between (UI automation waits for idle before reading the screen).
            val start = TimeSource.Monotonic.markNow()
            while (true) {
                val ticks = playback.ticksAt(start.elapsedNow())
                // A report a little behind the glide doesn't pull it back; a skip or seek does.
                if (ticks >= shown || shown - ticks > BacktrackAllowance) shown = ticks
                track.moveTo(shown, animate = false)
                placed = true
                delay(playback.timePerPixel(pixelsPerTick = track.itemSizePx / TicksPerMinute))
            }
        }
    }
    LaunchedEffect(track) {
        var wasScrolling = false
        snapshotFlow { track.isUserScrolling to track.tick }.collect { (scrolling, tick) ->
            // Dragged all the way: the book's end, which may lie between the last two lines.
            val at = if (track.isAtEnd) duration else TickLength * tick
            when {
                scrolling -> currentOnScrub(at)
                wasScrolling -> currentOnScrubEnd(at)
            }
            wasScrolling = scrolling
        }
    }

    // Hidden from accessibility: the label above already reads the position, and a ruler that scrolls
    // while playing would otherwise report a scroll a few times a second, so the screen never goes idle
    // for accessibility services (or UI automation, which waits for that).
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        Track(
            state = track,
            itemSize = 120.dp,
            firstGuideline = maxWidth / 2,
            showGuidelineDot = true,
            // The ruler glides between lines, so nothing is printed at the guideline: the exact position
            // is in the text above, and the ruler only labels whole minutes.
            showSelectedLabel = false,
            itemContent = { minute, _ -> Body(text = formatDuration(minute.minutes)) },
        )
    }
}

/** How far behind the glide, in ruler lines (one second), a report may be and still be ignored. */
private const val BacktrackAllowance = 0.1f

/** The shortest wait between two moves of a playing ruler: about a frame. */
private val MinStep = 16.milliseconds

/** What the position track follows, read as one value so a new report restarts the glide. */
private data class Playback(val position: Duration, val duration: Duration, val isPlaying: Boolean, val speed: Float) {

    /** How long the ruler takes to move one pixel while playing. */
    fun timePerPixel(pixelsPerTick: Float): Duration {
        val pixelsPerSecond = pixelsPerTick * speed / TickLength.inWholeSeconds
        return if (pixelsPerSecond > 0f) (1.0 / pixelsPerSecond).seconds.coerceAtLeast(MinStep) else MinStep
    }

    /** Where the ruler should be [elapsed] after this report, in ruler lines. */
    fun ticksAt(elapsed: Duration): Float {
        val played = if (isPlaying) elapsed * speed.toDouble() else Duration.ZERO
        return ((position + played).coerceAtMost(duration) / TickLength).toFloat()
    }
}

@Preview
@Composable
private fun PlayerPreview() {
    RisoTheme {
        PlayerContent(
            ui = PlayerUi(
                title = "Sample Book",
                author = "Sample Author",
                coverPath = null,
                chapterTitle = "Middle",
                position = 90.seconds,
                duration = 190.seconds,
                positionLabel = "1:30",
                durationLabel = "3:10",
                isPlaying = false,
                speed = 1f,
                speedLabel = "1.0×",
                chapters = emptyList(),
            ),
            onBackClick = {},
            onChaptersClick = {},
            onSpeedClick = {},
            onPlayPause = {},
            onSkipBack = {},
            onSkipForward = {},
            onScrub = {},
            onScrubEnd = {},
            modifier = Modifier.risoPaper(),
        )
    }
}
