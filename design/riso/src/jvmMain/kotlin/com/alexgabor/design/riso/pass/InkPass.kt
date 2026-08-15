package com.alexgabor.design.riso.pass

import androidx.compose.ui.graphics.RenderEffect

/** No runtime shaders here, so nothing is separated and the artwork is handed back as drawn. */
internal actual class InkPass actual constructor() {
    actual fun effect(spec: InkPassSpec): RenderEffect? = null
}
