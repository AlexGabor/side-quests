package com.alexgabor.design.riso.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.print.risoOverprint
import com.alexgabor.design.riso.print.risoPrint
import com.alexgabor.design.riso.separation.risoInk

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(targetValue = if (pressed) 0f else 2f)
    Box(
        modifier = modifier
            .clip(RisoTheme.shapes.standardShape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    try {
                        awaitReleaseOrCancel()
                    } finally {
                        pressed = false
                    }
                }
            }
            .risoInk(listOf(RisoTheme.colors.inks.fluorescentPink, RisoTheme.colors.inks.purple), offsetScale = offset)
            .background(risoOverprint(inks = arrayOf(RisoTheme.colors.inks.fluorescentPink to 1f,
                RisoTheme.colors.inks.purple to 1f)))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            textStyle = RisoTheme.typography.body,
            color = RisoTheme.colors.paper,
            modifier = Modifier.risoInk(listOf(RisoTheme.colors.inks.fluorescentPink, RisoTheme.colors.inks.purple), offsetScale = 0f)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

/**
 * Waits until all pointers are lifted or the gesture is taken over by an ancestor (e.g. a scroll).
 * Observed on the [PointerEventPass.Final] pass so parent consumption is visible.
 */
private suspend fun AwaitPointerEventScope.awaitReleaseOrCancel() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Final)
        if (event.changes.any { it.isConsumed && it.positionChange() != Offset.Zero }) return
        if (event.changes.none { it.pressed }) return
    }
}

@Preview
@Composable
private fun ButtonPreview() {
    var counter by remember { mutableIntStateOf(0) }
    RisoTheme {
        LazyColumn(
            Modifier.background(RisoTheme.colors.paper)
                .fillMaxSize()
                .risoPrint()
                .safeDrawingPadding()
                .padding(RisoTheme.dimens.screenPadding)
        ) {
            item {
                Button(
                    text = "Button $counter",
                    onClick = { counter += 1 },
                )
            }
        }
    }
}