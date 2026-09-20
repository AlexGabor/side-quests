package com.alexgabor.stamp.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * The three layers of an adaptive icon and the five buckets each of them is printed into, plus the
 * single flat print iOS takes instead of any of it.
 *
 * A layer is drawn to fill whatever it is handed, so its size is entirely the caller's business —
 * see [IconDensity] and [IOS_ICON_SCALE] for the sizes that matter, and [ICON_PRINTS] for which
 * combinations of the two are actually files.
 */

/** Android's adaptive-icon canvas. Only the inner 66 dp survives a launcher's mask. */
val ICON_SIDE = 108.dp

/** The one size an asset catalog takes for an iOS app icon. */
const val IOS_ICON_SIDE_PX = 1024

/** Where an export writes it, named so the tree can be copied straight over `Assets.xcassets/`. */
const val IOS_ICON_DIR = "AppIcon.appiconset"

/**
 * The density the iOS print is staged at.
 *
 * The canvas stays [ICON_SIDE] rather than becoming a 1024 dp one, because every size the press
 * works in — dot, grain, mottle, registration error — is in dp. Holding the canvas and moving the
 * density is what makes the iOS icon the *same print* as the Android one, larger; sized in dp
 * instead, it would come off a press with a nine times finer screen.
 */
val IOS_ICON_SCALE: Float = IOS_ICON_SIDE_PX / ICON_SIDE.value

/** The one size a Play Store listing takes for an app icon. */
const val PLAY_ICON_SIDE_PX = 512

/**
 * Where an export writes it. The file itself is named as Android Studio's Image Asset wizard names
 * it, which is also where it lands: beside `res/`, where it is kept but never packaged.
 */
const val PLAY_ICON_DIR = "playstore"

/** The density the Play print is staged at, held to the same canvas as [IOS_ICON_SCALE] and why. */
val PLAY_ICON_SCALE: Float = PLAY_ICON_SIDE_PX / ICON_SIDE.value

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

    override val text: String get() = qualifier.removePrefix("drawable-")
}

/**
 * Something the press stages on its own, and the file an exporter writes it to.
 *
 * Three of them are the layers of Android's adaptive icon, which a launcher composites itself. The
 * last two are not layers at all: iOS and the Play Store both take the finished icon flat, so [Ios]
 * and [Play] are each all three of the others' work done in a single pass.
 */
enum class IconLayer(override val text: String, val fileName: String) : ButtonGroupItem {
    Background("bg", "ic_launcher_background.png"),
    Foreground("fg", "ic_launcher_foreground.png"),
    Monochrome("mono", "ic_launcher_monochrome.png"),
    Ios("ios", "AppIcon.png"),
    Play("play", "ic_launcher-playstore.png"),
}

/** The three that make up an adaptive icon, as against the flat prints that finish alone. */
val ANDROID_ICON_LAYERS: List<IconLayer> =
    listOf(IconLayer.Background, IconLayer.Foreground, IconLayer.Monochrome)

@Composable
fun IconLayer.Content(modifier: Modifier = Modifier) {
    when (this) {
        IconLayer.Background -> IconBackground(modifier)
        IconLayer.Foreground -> IconForeground(modifier)
        IconLayer.Monochrome -> IconMonochrome(modifier)
        IconLayer.Ios, IconLayer.Play -> FlatIcon(modifier)
    }
}

/** One file an export writes, and the stage that prints it. */
data class IconPrint(
    val dir: String,
    val layer: IconLayer,
    val scale: Float,
) {
    val fileName: String get() = layer.fileName

    /** The side of the exported PNG, in pixels. */
    val sidePx: Int get() = (ICON_SIDE.value * scale).roundToInt()

    /**
     * Whether the PNG is written without an alpha channel.
     *
     * An asset catalog's app icon is rejected for carrying one, and Play shows its own background
     * through any transparency. [FlatIcon] prints on stock that covers the canvas, so there is
     * nothing to lose by dropping it. The adaptive layers keep theirs: a launcher has to be able to
     * see through the foreground.
     */
    val opaque: Boolean get() = layer == IconLayer.Ios || layer == IconLayer.Play
}

/** The iOS print, which has one size and no bucket to be selected from. */
val IOS_ICON_PRINT = IconPrint(IOS_ICON_DIR, IconLayer.Ios, IOS_ICON_SCALE)

/** The Play Store print, which likewise has one size. */
val PLAY_ICON_PRINT = IconPrint(PLAY_ICON_DIR, IconLayer.Play, PLAY_ICON_SCALE)

/**
 * Everything an export writes: the three adaptive layers in each of the five buckets, and the two
 * flat prints. Stated as a list rather than as a cross product, because neither flat print is a cell
 * of that grid — each has one size, and is the only layer of itself.
 */
val ICON_PRINTS: List<IconPrint> =
    IconDensity.entries.flatMap { bucket ->
        ANDROID_ICON_LAYERS.map { layer -> IconPrint(bucket.qualifier, layer, bucket.scale) }
    } + IOS_ICON_PRINT + PLAY_ICON_PRINT

/**
 * The print a layer and a bucket name between them — the inverse of [ICON_PRINTS], for a screen that
 * offers the two as separate choices.
 *
 * The flat prints answer with their own whatever bucket is held alongside them, since the buckets
 * are Android resource qualifiers and none of them means anything to an asset catalog or a listing.
 */
fun printOf(layer: IconLayer, bucket: IconDensity): IconPrint = when (layer) {
    IconLayer.Ios -> IOS_ICON_PRINT
    IconLayer.Play -> PLAY_ICON_PRINT
    else -> IconPrint(bucket.qualifier, layer, bucket.scale)
}

/**
 * The sheet, with nothing printed on it. Every other layer is composited over this one.
 */
@Composable
private fun IconBackground(modifier: Modifier) {
    Box(modifier.risoPaper(RisoPaper()))
}

/**
 * The mark, printed and then cut out of its own white.
 *
 * The sheet is [RisoPaper.None] rather than the stock, because a launcher composites this layer over
 * the background one and would otherwise print the paper twice. With no stock behind the ink, what
 * comes off the press is the inks' own transmittance — ink as if held up to the light. The
 * separation is unaffected: a sheet that paints no stock leaves `risoInk` to resolve coverage
 * against `RisoTheme.colors.paper`, as the background layer's stock is.
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

/**
 * The whole icon on one sheet: iOS and the Play Store composite nothing, so the mark is simply
 * printed onto the stock.
 *
 * This is [IconBackground] and [IconForeground] in one pass rather than two, and the difference is
 * not only that it saves a file. The foreground prints on [RisoPaper.None] and is then cut back by
 * [drawPacerMask] purely so that a *launcher* can lay it over the background — off a real press, ink
 * lands on paper and is shaded by it. With nothing left to composite, the print can be that one: the
 * mark takes the sheet's own surface, and there is no mask to cut, because what comes off the press
 * covers the canvas.
 *
 * Which is the other thing both want. An iOS app icon is rejected for carrying an alpha channel, Play
 * asks for a solid background, and a layer printed on real stock has no alpha to carry — see
 * [IconPrint.opaque]. Play also wants the square left square: it rounds the corners and adds the
 * shadow itself.
 *
 * It is also fitted wider than the adaptive layers are, to [PACER_IOS_FIT_RADIUS]: there is no
 * circular mask here to leave room for. Play's rounded square, at a 30% corner radius, still spares
 * the whole inscribed circle, so the same fit serves it.
 */
@Composable
private fun FlatIcon(modifier: Modifier) {
    Box(modifier.risoPaper(RisoPaper())) {
        Canvas(
            Modifier
                .fillMaxSize()
                .risoInk(
                    first = PacerIconContour,
                    second = PacerIconSplash,
                    offsetScale = ICON_REGISTRATION_SCALE,
                ),
        ) {
            drawPacerStopwatch(fitRadius = PACER_IOS_FIT_RADIUS)
        }
    }
}
