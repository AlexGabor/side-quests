package com.alexgabor.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.risoInk

/**
 * Where the pointer is, for the take to read.
 *
 * The same state the script sends to the scene, so what is drawn and what the components react to
 * cannot come apart.
 */
class Pointer internal constructor() {
    var position by mutableStateOf(Offset.Zero)
        internal set
    var pressed by mutableStateOf(false)
        internal set
    var shown by mutableStateOf(false)
        internal set
}

/** How far the arrow slides into the sheet while the button is held. */
private val PressNudge = 1.5.dp

/**
 * The pointer, drawn.
 *
 * A recording has no cursor of its own, and hover in particular is unreadable without one: a segment
 * lighting up on its own reads as an animation rather than as a response. Printed like everything
 * else — one pass of black, with the stock left showing around the arrow so it stays legible over
 * ink as well as over paper.
 */
@Composable
fun Cursor(pointer: Pointer, modifier: Modifier = Modifier) {
    val ink = RisoTheme.colors.inks.vintageBlack
    val paper = RisoTheme.colors.paper
    Canvas(modifier = modifier.risoInk(ink)) {
        if (!pointer.shown) return@Canvas
        val nudge = if (pointer.pressed) PressNudge.toPx() else 0f
        val arrow = arrowPath(scale = density)
        translate(pointer.position.x + nudge, pointer.position.y + nudge) {
            // The outline first and wider than the fill, so what is left of it around the arrow is
            // a hairline of bare stock rather than a second color.
            drawPath(arrow, color = paper, style = Stroke(width = 3.dp.toPx()))
            drawPath(arrow, color = ink)
        }
    }
}

/** A cursor arrow with its tip at the origin, in dp scaled by [scale]. */
private fun arrowPath(scale: Float): Path {
    val points = listOf(
        0f to 0f,
        0f to 17f,
        4.4f to 13.2f,
        7.2f to 19f,
        10.2f to 17.6f,
        7.3f to 12f,
        12.4f to 11.7f,
    )
    return Path().apply {
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(x * scale, y * scale) else lineTo(x * scale, y * scale)
        }
        close()
    }
}
