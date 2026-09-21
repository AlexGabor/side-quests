package com.alexgabor.design.riso.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Keeps every press away from this subtree while [block] is true: content on its way out, which is
 * still on the page and still clickable as far as it knows.
 */
internal fun Modifier.blockPointerInput(block: Boolean): Modifier = pointerInput(block) {
    if (!block) return@pointerInput
    // Before the content sees them, which is what the initial pass is for: a consumed press never
    // reaches the clickable underneath.
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
