package com.alexgabor.stamp

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.region.risoBypass
import com.alexgabor.stamp.icon.Content
import com.alexgabor.stamp.icon.ICON_PRINTS
import com.alexgabor.stamp.icon.ICON_SIDE
import com.alexgabor.stamp.icon.IconDensity
import com.alexgabor.stamp.icon.IconLayer
import com.alexgabor.stamp.icon.IconPrint
import com.alexgabor.stamp.icon.printOf
import kotlinx.coroutines.launch

/** Where exported prints are written. */
interface IconExporter {
    /** Where the files land, as something short enough for a status line. */
    val destination: String

    /**
     * Writes [image] as [fileName] inside [dir] of [destination], replacing what was there.
     *
     * [opaque] drops the alpha channel on the way out — see [IconPrint.opaque] for who needs that.
     */
    suspend fun write(dir: String, fileName: String, opaque: Boolean, image: ImageBitmap)
}

/** How wide the magnified view of the last capture is drawn. */
private val InspectorSide = 240.dp

/**
 * The press, pointed at a launcher icon.
 *
 * One layer at one density is staged at a time, at its true pixel size, and that stage is what the
 * [IconExporter] is handed: `record` captures a single display list, and that one recording is
 * rasterized twice — once onto the screen, once offscreen into the PNG. There is no separate export
 * render that could quietly disagree with what is displayed. Exporting is just walking the stage
 * through every print in [ICON_PRINTS].
 *
 * Which is also why the stage is never scaled, clipped or *constrained* by anything above it. The
 * first two would only mislead — an ancestor's transform reaches the screen but not `toImageBitmap`
 * — but the third reaches the PNG: a stage squeezed into a narrower parent is laid out smaller, and
 * exports smaller. Magnification lives in the inspector below, which blows up the bitmap that was
 * read back rather than the stage that produced it.
 */
@Composable
fun StampScreen(
    exporter: IconExporter,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val capture = rememberGraphicsLayer()

    var bucket by remember { mutableStateOf(IconDensity.Xxxhdpi) }
    var staged by remember { mutableStateOf(printOf(IconLayer.Foreground, IconDensity.Xxxhdpi)) }
    var inspected by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    /** Runs [block] only if nothing else is using the stage, so two captures never overlap. */
    fun onStage(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (error: Throwable) {
                status = error.message ?: error.toString()
            } finally {
                busy = false
            }
        }
    }

    /** Stages one print and reads back what it printed. */
    suspend fun stage(next: IconPrint): ImageBitmap {
        staged = next
        return capture.settled().also { inspected = it }
    }

    LaunchedEffect(Unit) { stage(staged) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(RisoTheme.dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading1("Stamp", Modifier.risoInk(RisoTheme.colors.content).fillMaxWidth())

        // The stage sits in a box the size of the largest print, so the controls below it do not
        // jump as the prints are stepped through. Measured in the device's own density — the
        // override is for the stage alone.
        //
        // `requiredSize`, because the box is wider than the column's padding leaves room for at the
        // iOS print's 1024 px and `size` would hand the stage the narrower constraint instead. An
        // ancestor's *clip* never reaches `toImageBitmap`, but its constraints do, and a stage laid
        // out at 996 px exports a 996 px PNG. The overflow is only drawn over the padding.
        Box(
            modifier = Modifier.requiredSize(
                with(LocalDensity.current) { ICON_PRINTS.maxOf { it.sidePx }.toDp() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            // The print's scale *is* the density: 108 dp at 4x is the 432 px the xxxhdpi folder
            // holds, at 4.74x the 512 px a Play listing takes, and at 9.48x the 1024 px an asset
            // catalog takes. Fixed rather than taken from
            // the device, because the press's dot, grain and mottle sizes are all in dp — left to
            // the device's density, the same icon would print coarser on one phone than on another.
            CompositionLocalProvider(LocalDensity provides Density(staged.scale, fontScale = 1f)) {
                Box(
                    modifier = Modifier
                        .size(ICON_SIDE)
                        // The screen is a printed page too, and the stage has its own sheet under
                        // it. Without this window the outer press would read the layer's pixels as
                        // ink and print them a second time, and the stage would stop matching the
                        // PNG — which is recorded before any of that reaches it.
                        .risoBypass()
                        .drawWithContent {
                            capture.record { this@drawWithContent.drawContent() }
                            drawLayer(capture)
                        },
                ) {
                    staged.layer.Content(Modifier.fillMaxSize())
                }
            }
        }

        // The pixels that came back off the stage, blown up unfiltered. An mdpi layer is 108 px,
        // which is a thumbnail on a modern phone; this is the only way to see what the press did.
        inspected?.let { image ->
            Image(
                painter = BitmapPainter(image, filterQuality = FilterQuality.None),
                contentDescription = "${staged.layer.text} at ${staged.sidePx} px, magnified",
                modifier = Modifier.size(InspectorSide).risoBypass(),
            )
        }

        ButtonGroup(
            selected = staged.layer,
            *IconLayer.entries.toTypedArray(),
            onSelect = { next -> onStage { stage(printOf(next, bucket)) } },
        )

        // Five buckets do not fit across a phone at the group's own padding, so this one scrolls —
        // and the one it opens on is the last of them, so it starts scrolled to the end rather than
        // with its own selection off the side.
        //
        // The selection stays live while a flat print (ios or play) is staged even though it changes
        // nothing there: it is the bucket the group will come back to, not a claim about what is on
        // stage.
        val buckets = rememberScrollState()
        LaunchedEffect(Unit) { buckets.scrollTo(buckets.maxValue) }
        Box(Modifier.horizontalScroll(buckets)) {
            ButtonGroup(
                selected = bucket,
                *IconDensity.entries.toTypedArray(),
                onSelect = { next ->
                    onStage {
                        bucket = next
                        stage(printOf(staged.layer, next))
                    }
                },
            )
        }

        Body(
            text = "${staged.dir} · ${staged.sidePx} px · ${staged.fileName}",
            modifier = Modifier.risoInk(RisoTheme.colors.content),
        )

        Button(
            text = "Export all",
            onClick = {
                onStage {
                    var written = 0
                    ICON_PRINTS.forEach { print ->
                        exporter.write(
                            dir = print.dir,
                            fileName = print.fileName,
                            opaque = print.opaque,
                            image = stage(print),
                        )
                        written++
                        status = "$written / ${ICON_PRINTS.size}"
                    }
                    status = "Wrote $written prints to ${exporter.destination}"
                }
            },
        )

        if (status.isNotEmpty()) {
            Body(status, Modifier.risoInk(RisoTheme.colors.content))
        }
    }
}

/**
 * Reads this layer back once the composition has caught up with whatever was just staged.
 *
 * Two frames: the first composes and lays the stage out at its new size, the second draws it. Only
 * once the draw has run has `record` captured the combination that is staged now rather than the one
 * before it.
 */
private suspend fun GraphicsLayer.settled(): ImageBitmap {
    withFrameNanos {}
    withFrameNanos {}
    return toImageBitmap()
}
