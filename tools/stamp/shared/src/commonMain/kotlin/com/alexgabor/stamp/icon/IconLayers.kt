package com.alexgabor.stamp.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.RisoPaper
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlin.math.roundToInt

/**
 * The three layers of an adaptive icon, and the five buckets each of them is printed into.
 *
 * A layer is drawn to fill whatever it is handed, so its size is entirely the caller's business —
 * see [IconDensity] for the sizes that matter.
 */

/** Android's adaptive-icon canvas. Only the inner 66 dp survives a launcher's mask. */
val ICON_SIDE = 108.dp

/**
 * How much of the press's registration error this print takes.
 *
 * A drum lands 3 dp off ([risoInkForSlot][com.alexgabor.design.riso.risograph.inks.RisoInk]), which
 * on a 108 dp canvas is most of the stopwatch's crown. Damped, the passes still separate visibly at
 * the edges — which is the whole point — without the mark coming apart.
 */
private const val ICON_REGISTRATION_SCALE = 0.45f

/**
 * A density bucket, which for an icon is the same thing as a size: the layer is 108 dp wide in every
 * one of them, so the bucket's scale is the only thing that changes.
 *
 * Named for the resource qualifier, so an export writes a tree that can be copied straight over
 * `res/`. `ldpi` is not among them: it has been deprecated for years and nothing that shows an
 * adaptive icon runs at it.
 */
enum class IconDensity(val qualifier: String, val scale: Float) : ButtonGroupItem {
    Mdpi("drawable-mdpi", 1f),
    Hdpi("drawable-hdpi", 1.5f),
    Xhdpi("drawable-xhdpi", 2f),
    Xxhdpi("drawable-xxhdpi", 3f),
    Xxxhdpi("drawable-xxxhdpi", 4f);

    /** The side of the exported PNG, in pixels. */
    val sidePx: Int get() = (ICON_SIDE.value * scale).roundToInt()

    override val text: String get() = qualifier.removePrefix("drawable-")
}

/** One layer of the adaptive icon, and the file an exporter writes it to. */
enum class IconLayer(override val text: String, val fileName: String) : ButtonGroupItem {
    Background("bg", "ic_launcher_background.png"),
    Foreground("fg", "ic_launcher_foreground.png"),
    Monochrome("mono", "ic_launcher_monochrome.png"),
}

@Composable
fun IconLayer.Content(modifier: Modifier = Modifier) {
    when (this) {
        IconLayer.Background -> IconBackground(modifier)
        IconLayer.Foreground -> IconForeground(modifier)
        IconLayer.Monochrome -> IconMonochrome(modifier)
    }
}

/**
 * The sheet, with nothing printed on it. Every other layer is composited over this one.
 *
 * The white is not a colour, it is a blank pass: white is full transmittance, so the press lays no
 * ink and hands back the stock alone. It is here because a sheet still has to go *through* the
 * press — `risoPaper` is a render effect over a layer's own pixels, and a layer with nothing drawn
 * in it is never rasterized at all, so an empty box comes back empty rather than papered.
 */
@Composable
private fun IconBackground(modifier: Modifier) {
    Box(modifier.risoPaper(RisoPaper()).drawBehind { drawRect(Color.White) })
}

/**
 * The mark, printed and then cut out of its own white.
 *
 * The sheet is [RisoPaper.None] rather than the stock, because a launcher composites this layer over
 * the background one and would otherwise print the paper twice. With no stock behind the ink, what
 * comes off the press is the inks' own transmittance — ink as if held up to the light. The
 * separation is unaffected: `risoInk` resolves coverage against `RisoTheme.colors.paper` whatever
 * sheet it is printed on.
 *
 * What that leaves is opaque: transmittance is white where no drum reached, which is correct for the
 * multiply the passes are drawn with and useless to a launcher compositing this over a background.
 * So [drawPacerMask] cuts it back to the shape the mark could have printed into.
 */
@Composable
private fun IconForeground(modifier: Modifier) {
    // Where each drum lands, so the mask can follow the ink rather than the artwork. Resolved to
    // pixels out here rather than in the draw: `DrawScope.record` hands the layer the enclosing
    // scope as its own density, so that scope ends up resolving its density through itself and a
    // `toPx()` inside a record block recurses until the stack runs out.
    val press = RisoTheme.press
    val density = LocalDensity.current
    val registration = remember(press, density) {
        fun of(ink: Color): Offset = press.inks[press.slotOf(ink)].let { drum ->
            with(density) { Offset(drum.offsetX.dp.toPx(), drum.offsetY.dp.toPx()) } *
                    ICON_REGISTRATION_SCALE
        }
        of(PacerIconContour) to of(PacerIconSplash)
    }
    val mask = rememberGraphicsLayer()

    Box(
        modifier
            // DstIn needs somewhere of its own to work. Without this the mask would cut into
            // everything already on the canvas rather than into the print alone.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                mask.blendMode = BlendMode.DstIn
                mask.record { drawPacerMask(registration.first, registration.second) }
                drawLayer(mask)
            }
            .risoPaper(RisoPaper.None),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .risoInk(
                    first = PacerIconContour,
                    second = PacerIconSplash,
                    offsetScale = ICON_REGISTRATION_SCALE,
                ),
        ) {
            drawPacerStopwatch()
        }
    }
}

/**
 * The mark as a flat silhouette, off the press entirely.
 *
 * A themed icon is tinted from its alpha alone, so a screen, a grain and two passes landing a few
 * pixels apart would come out as holes in the mark rather than as texture.
 */
@Composable
private fun IconMonochrome(modifier: Modifier) {
    Canvas(modifier) { drawPacerSilhouette() }
}
