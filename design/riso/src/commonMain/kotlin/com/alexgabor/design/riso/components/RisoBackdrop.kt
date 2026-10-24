package com.alexgabor.design.riso.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.LocalRisoEffectsEnabled
import com.alexgabor.design.riso.risograph.inks.risoFade

/**
 * How hard the press runs over the page while sheets lie on it.
 *
 * Each open sheet reports how much of it is on the page, `0` to `1`. The page prints at full ink
 * with nothing on it and at [BottomSheetDefaults.BackdropFade] under a sheet that is fully open, in
 * proportion in between — so dragging a sheet away gives the page its ink back as it goes.
 */
@Stable
internal class RisoBackdrop {

    private val shown = mutableStateMapOf<Any, Float>()

    /** The ink the page behind every open sheet is printed at. */
    val fade: Float by derivedStateOf { backdropFade(shown.values.maxOrNull() ?: 0f) }

    fun report(sheet: Any, shown: Float) {
        this.shown[sheet] = shown.coerceIn(0f, 1f)
    }

    fun remove(sheet: Any) {
        shown.remove(sheet)
    }
}

/** The page's ink when a sheet is [shown] that much: full at `0`, the backdrop fade at `1`. */
internal fun backdropFade(shown: Float): Float =
    1f - (1f - BottomSheetDefaults.BackdropFade) * shown.coerceIn(0f, 1f)

/** The backdrop the page under this point is printed through, provided by [RisoTheme]. */
internal val LocalRisoBackdrop = staticCompositionLocalOf<RisoBackdrop?> { null }

/**
 * Prints [content] as the page that sheets are laid over — see [BottomSheet].
 *
 * The page is run lighter rather than covered: [risoFade] thins every pass inside, so what is
 * behind a sheet is smaller dots of the same ink on the same paper, the way a screen thins in a
 * crossfade. With the press stood down a fade at the page's root would be an alpha over the paper
 * too, letting the window through; the paper laid back over the content is the flat stand-in.
 */
@Composable
internal fun RisoBackdrop(content: @Composable () -> Unit) {
    val backdrop = remember { RisoBackdrop() }
    val effectsEnabled = LocalRisoEffectsEnabled.current
    val modifier = if (effectsEnabled) {
        Modifier.risoFade(backdrop.fade)
    } else {
        val paper = RisoTheme.colors.paper
        Modifier.drawWithContent {
            drawContent()
            val fade = backdrop.fade
            if (fade < 1f) drawRect(paper, alpha = 1f - fade)
        }
    }
    CompositionLocalProvider(LocalRisoBackdrop provides backdrop) {
        Box(modifier, propagateMinConstraints = true) {
            content()
        }
    }
}
