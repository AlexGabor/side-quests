package com.alexgabor.design.riso.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.Card
import com.alexgabor.design.riso.risograph.inks.onRisoPaper
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.inks.risoOverprint
import com.alexgabor.design.riso.risograph.paper.RisoPaper
import com.alexgabor.design.riso.risograph.paper.risoPaper
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * Renders still snapshots of one fixed test page, at a phone size and two desktop sizes, so that a
 * change to the paper or the ink can be compared frame for frame against what came before it.
 *
 * `./gradlew :design:risoRecorder:snapshots` — the PNGs land in `build/snapshots`.
 */
fun main(args: Array<String>) {
    val output = File(args.getOrNull(0) ?: error("Usage: snapshots <output dir>")).apply { mkdirs() }

    listOf(
        Shot("phone", widthDp = 390, heightDp = 844, density = 3f) { SnapshotPage() },
        Shot("desktop", widthDp = 1440, heightDp = 900, density = 2f) { SnapshotPage() },
        Shot("wide", widthDp = 1920, heightDp = 1080, density = 2f) { SnapshotPage() },
        // Nothing but a textured sheet, as large as a 4K screen, to look for the surface repeating.
        Shot("texture", widthDp = 1920, heightDp = 1080, density = 2f) {
            Box(Modifier.fillMaxSize().risoPaper(TexturedStock))
        },
    ).forEach { shot ->
        val started = System.currentTimeMillis()
        val file = output.resolve("${shot.name}.png")
        file.writeBytes(snapshot(shot, shot.content))
        println("${file.path}  ${System.currentTimeMillis() - started}ms")
    }

    exitProcess(0)
}

private class Shot(
    val name: String,
    val widthDp: Int,
    val heightDp: Int,
    val density: Float,
    val content: @Composable () -> Unit,
)

private fun snapshot(shot: Shot, content: @Composable () -> Unit): ByteArray {
    val dispatcher = QueueDispatcher()
    return ImageComposeScene(
        width = (shot.widthDp * shot.density).toInt(),
        height = (shot.heightDp * shot.density).toInt(),
        density = Density(shot.density),
        coroutineContext = dispatcher,
        content = { RisoTheme { content() } },
    ).use { scene ->
        dispatcher.settle(scene, timeoutMillis = 120_000)
        // Settling only proves nothing changed for a few frames, and a surface still being baked
        // changes nothing until it lands — so wait out any bake in flight and settle again.
        Thread.sleep(3_000)
        dispatcher.settle(scene, timeoutMillis = 120_000)
        scene.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}

/** A stock visibly unlike the default, for the nested sheet. */
private val PinkStock = Color(0xFFF6D9E1)

/**
 * The default stock with a darker back, so the surface's lighting shows — on the default stock the
 * front and back are one color and the lighting cancels out.
 */
private val TexturedStock = RisoPaper(colorBack = Color(0xFFB9AF9C), contrast = 0.4f)

/**
 * Everything the paper and the ink have to get right, on one page: type, a component, solid and
 * overprinted inks, content that is not printed at all, and a sheet of a different stock inside the
 * page's own.
 */
@Composable
private fun SnapshotPage() {
    val colors = RisoTheme.colors
    val typography = RisoTheme.typography
    Column(
        modifier = Modifier.fillMaxSize().risoPaper().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            text = "Riso sheet",
            style = typography.heading1.copy(color = colors.content),
            modifier = Modifier.risoInk(colors.content),
        )
        BasicText(
            text = "Printed on the drums named, off register, screened and mottled, on a sheet " +
                "with a surface of its own.",
            style = typography.body.copy(color = colors.content),
            modifier = Modifier.risoInk(colors.content),
        )
        Button(text = "Print", onClick = {})

        Label("Inks")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(colors.inks.fluorescentPink, colors.inks.blue, colors.inks.yellow).forEach { ink ->
                Box(Modifier.size(64.dp).risoInk(ink).background(ink.onRisoPaper()))
            }
            val pink = colors.inks.fluorescentPink
            val blue = colors.inks.blue
            Box(
                Modifier.size(64.dp)
                    .risoInk(pink, blue)
                    .background(risoOverprint(inks = listOf(pink to 0.6f, blue to 0.6f))),
            )
        }

        Label("Not printed")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).background(colors.accent.onRisoPaper()))
            Box(
                Modifier.size(64.dp).background(
                    Brush.linearGradient(listOf(Color(0xFFE0533D), Color(0xFF2A7AB8))),
                ),
            )
        }

        Box(
            Modifier.fillMaxWidth()
                .height(96.dp)
                .risoPaper(RisoPaper(colorFront = PinkStock, colorBack = PinkStock))
                .padding(16.dp),
        ) {
            BasicText(
                text = "On pink stock",
                style = typography.heading2.copy(color = colors.content),
                modifier = Modifier.risoInk(colors.content),
            )
        }

        Box(
            Modifier.fillMaxWidth()
                .height(160.dp)
                .risoPaper(TexturedStock)
                .padding(16.dp),
        ) {
            BasicText(
                text = "Textured stock",
                style = typography.heading2.copy(color = colors.content),
                modifier = Modifier.risoInk(colors.content),
            )
        }

        Card(onClick = {}) {
            BasicText(
                text = "A card",
                style = typography.body.copy(color = colors.content),
                modifier = Modifier.padding(16.dp).risoInk(colors.content),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    BasicText(
        text = text,
        style = RisoTheme.typography.heading3.copy(color = RisoTheme.colors.content),
        modifier = Modifier.risoInk(RisoTheme.colors.content),
    )
}
