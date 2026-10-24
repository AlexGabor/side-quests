package com.alexgabor.design.riso.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.risoFade
import com.alexgabor.design.riso.risograph.inks.risoFadeAsAlpha
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A modal sheet laid over the bottom of the page: open for as long as it is composed, dismissed by
 * dragging or flinging it down, by back (predictive on Android), by a tap outside it, or by its
 * content through [BottomSheetScope.dismiss]. Every one of those animates it away first and then
 * calls [onDismissRequest], where the caller stops composing it.
 *
 * It is as tall as its content and opens straight to that height, up to the height of the window;
 * content taller than that has to scroll, and a list at its top hands a downward drag to the sheet.
 *
 * ### No scrim
 * Nothing is laid over the page behind. It is printed lighter instead — the press runs at
 * [BottomSheetDefaults.BackdropFade] of its ink under a sheet that is fully open, and gives the
 * page its ink back as the sheet is dragged away — so the page is smaller dots of the same ink on
 * the same paper, as a screen is part-way through a crossfade. See [risoFade]: content the press
 * does not print, such as an image, keeps its strength unless it asks for the fade with
 * [risoFadeAsAlpha]. Nothing behind the sheet takes input while it is open.
 *
 * The sheet itself is a second piece of paper on the first: a stock of its own, edged in ink along
 * its top and sides, that the content prints on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BottomSheetScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val onDismiss by rememberUpdatedState(onDismissRequest)
    val dismisser = remember(sheetState, coroutineScope) {
        SheetDismisser(sheetState, coroutineScope) { onDismiss() }
    }

    // Report how much of the sheet is on the page to the page it lies on, which prints lighter
    // by as much. It forgets the sheet when the sheet leaves, whatever way it leaves.
    val backdrop = LocalRisoBackdrop.current
    val key = remember { Any() }
    DisposableEffect(backdrop, key) { onDispose { backdrop?.remove(key) } }

    val shape = RisoTheme.shapes.standardShape.topOnly()
    ModalBottomSheet(
        // Through the dismisser, so a sheet its content is already dismissing is reported once.
        onDismissRequest = dismisser::gone,
        // Never the whole window: some of the page always shows above the sheet, to be tapped.
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = BottomSheetDefaults.TopGap),
        sheetState = sheetState,
        sheetMaxWidth = RisoTheme.dimens.contentMaxWidth,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = RisoTheme.colors.content,
        tonalElevation = 0.dp,
        scrimColor = Color.Transparent,
        dragHandle = null,
        // Taken inside, so the paper reaches the bottom of the window and only the content stops
        // short of the system bars.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        SheetPaper(
            shape = shape,
            modifier = modifier.onGloballyPositioned { backdrop?.report(key, it.shownFraction()) },
        ) {
            val sheetScope = remember(this, dismisser) { BottomSheetScopeImpl(this, dismisser::dismiss) }
            sheetScope.content()
        }
    }
}

/** What a [BottomSheet]'s content can do besides lay itself out. */
@Stable
interface BottomSheetScope : ColumnScope {

    /** Animates the sheet away, then calls its `onDismissRequest`. Calling it again does nothing. */
    fun dismiss()
}

object BottomSheetDefaults {

    /** The ink the page behind a fully open sheet is printed at. */
    const val BackdropFade: Float = 0.35f

    /** The least of the page left showing above a sheet, below the top safe-drawing inset. */
    val TopGap: Dp = 48.dp
}

@Composable
private fun SheetPaper(
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RisoTheme.colors
    val lineWidth = RisoTheme.dimens.lineWidth
    Box(modifier.fillMaxWidth().risoPaper()) {
        // The edge prints on a pass of its own, behind the content rather than around it, so
        // nothing the content draws runs through the edge's drum. In register: the edge runs along
        // the sheet's own clip, so a drum's throw would push one side off the paper and leave
        // the other short of it.
        Box(
            Modifier
                .matchParentSize()
                .risoInk(colors.content, offsetScale = 0f)
                .drawWithCache {
                    val stroke = lineWidth.toPx()
                    // The shape run on past the bottom of the paper, so only its sides and top
                    // land on the sheet: the bottom edge is the window's.
                    val outline = shape.createOutline(
                        Size(size.width - stroke, size.height + stroke * 2),
                        layoutDirection,
                        this,
                    )
                    onDrawBehind {
                        clipRect {
                            translate(stroke / 2, stroke / 2) {
                                drawOutline(outline, colors.content, style = Stroke(stroke))
                            }
                        }
                    }
                },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val colors = RisoTheme.colors
    Box(
        modifier
            .padding(vertical = 12.dp)
            .semantics { contentDescription = "Drag handle" }
            .risoInk(colors.content)
            .size(width = 32.dp, height = 4.dp)
            .background(colors.content.copy(alpha = 0.5f), RisoTheme.shapes.pillShape),
    )
}

/** Rounded at the top, square where the sheet meets the bottom of the window. */
private fun CornerBasedShape.topOnly(): CornerBasedShape =
    copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))

/** How much of this sheet is on the page, from its top edge against the bottom of the window. */
private fun LayoutCoordinates.shownFraction(): Float {
    if (size.height == 0) return 0f
    val root = findRootCoordinates()
    val top = root.localPositionOf(this, Offset.Zero).y
    return ((root.size.height - top) / size.height).coerceIn(0f, 1f)
}

/** Hides the sheet, then reports it gone, once however many ways it goes. */
@OptIn(ExperimentalMaterial3Api::class)
private class SheetDismisser(
    private val state: SheetState,
    private val scope: CoroutineScope,
    private val onDismissed: () -> Unit,
) {
    private var dismissing = false
    private var gone = false

    fun dismiss() {
        if (dismissing || gone) return
        dismissing = true
        scope.launch { state.hide() }.invokeOnCompletion {
            if (!state.isVisible) gone() else dismissing = false
        }
    }

    fun gone() {
        if (gone) return
        gone = true
        onDismissed()
    }
}

private class BottomSheetScopeImpl(
    column: ColumnScope,
    private val onDismiss: () -> Unit,
) : BottomSheetScope, ColumnScope by column {
    override fun dismiss() = onDismiss()
}
