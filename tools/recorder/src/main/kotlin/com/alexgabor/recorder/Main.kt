package com.alexgabor.recorder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.ButtonGroupItem
import java.io.File
import kotlin.system.exitProcess

/**
 * Records the showcases in the README.
 *
 * `./gradlew :tools:recorder:run`, or `-Ptakes=pacer` for one of them.
 *
 * Takes the repository root and a folder to stage frames in, both of which the Gradle task fills in,
 * and optionally the names of the takes to record.
 */
fun main(args: Array<String>) {
    val root = File(args.getOrNull(0) ?: error("Usage: recorder <repo root> <frames dir> [takes]"))
    val frames = File(args.getOrNull(1) ?: error("Usage: recorder <repo root> <frames dir> [takes]"))
    val wanted = args.getOrNull(2)?.split(",")?.map(String::trim)?.toSet()

    val takes = listOf(
        Showcase("button", "design/riso/docs") { recordButton(frames) },
        Showcase("button-group", "design/riso/docs") { recordButtonGroup(frames) },
        Showcase("pacer", "pacer/docs") { recordPacer(frames) },
    )

    takes.filter { wanted == null || it.name in wanted }
        .forEach { take -> encode(take.record(), root.resolve(take.output), take.scale) }

    // Skiko and the coroutine machinery both hold threads that outlive the last frame.
    exitProcess(0)
}

/** One README recording: what it is called, where it lands, and how it is made. */
private class Showcase(
    val name: String,
    val output: String,
    val scale: Float = 1f,
    val record: () -> Recording,
)

/**
 * The press: two of them, one held long enough for the drums to come fully into register and one let
 * go before they get there.
 */
private fun recordButton(into: File): Recording = record(
    name = "button",
    into = into,
    maxFrames = 4.seconds,
    content = { Button(text = "Print", onClick = {}) },
) { frame ->
    val target = Offset(size.width / 2f, size.height / 2f)
    val approach = target + Offset(dp(40f), dp(22f))
    when (frame) {
        16 -> enter(approach)
        in 17..28 -> moveTo(lerp(approach, target, (frame - 16) / 12f))
        34 -> press()
        70 -> release()
        100 -> press()
        118 -> release()
        134 -> exit()
    }
    frame < 3.seconds - 1
}

/**
 * Hover and press, across all three segments and back to where it started, so the loop closes on the
 * state it opened in.
 */
private fun recordButtonGroup(into: File): Recording {
    // Read once, off the settled layout: the segments are as wide as their labels, so the sweep is
    // laid out around where they actually are.
    var bounds: List<Rect> = emptyList()

    return record(
        name = "button-group",
        into = into,
        maxFrames = 350,
        content = {
            var selected by remember { mutableStateOf(Segment.Left) }
            ButtonGroup(
                selected,
                Segment.Left,
                Segment.Middle,
                Segment.Right,
                onSelect = { selected = it },
            )
        },
    ) { frame ->
        if (bounds.isEmpty()) {
            bounds = segments()
            check(bounds.size == 3) { "Expected three segments, found ${bounds.size}" }
        }
        val (left, middle, right) = bounds.map { it.center }
        val entry = left + Offset(-dp(26f), dp(22f))

        when (frame) {
            12 -> enter(entry)
            in 13..30 -> moveTo(lerp(entry, left, (frame - 12) / 18f))
            in 44..70 -> moveTo(lerp(left, middle, (frame - 44) / 26f))
            78 -> press()
            90 -> release()
            in 108..134 -> moveTo(lerp(middle, right, (frame - 108) / 26f))
            142 -> press()
            154 -> release()
            in 172..198 -> moveTo(lerp(right, left, (frame - 172) / 26f))
            206 -> press()
            218 -> release()
            in 232..244 -> moveTo(lerp(left, entry, (frame - 232) / 12f))
            246 -> exit()
        }
        frame < 274
    }
}

private enum class Segment(override val text: String) : ButtonGroupItem {
    Left("Left"),
    Middle("Middle"),
    Right("Right"),
}

private val Int.seconds: Int get() = this * FrameRate

private fun lerp(from: Offset, to: Offset, fraction: Float): Offset =
    from + (to - from) * fraction.coerceIn(0f, 1f)

/**
 * The frames as one looping animation.
 *
 * WebP rather than a GIF: the print is grain and halftone dots all the way down, and a 256-color
 * palette turns that into banding — and rather than a video, because an image is the only thing that
 * plays inline in a README wherever it is read.
 */
private fun encode(recording: Recording, into: File, scale: Float) {
    into.mkdirs()
    val target = into.resolve("${recording.name}.webp")
    val process = ProcessBuilder(
        buildList {
            add("ffmpeg")
            add("-y")
            add("-loglevel"); add("error")
            add("-framerate"); add(recording.rate.toString())
            add("-i"); add(recording.frames.resolve("frame_%04d.png").path)
            if (scale != 1f) {
                // Rounded to an even width, which the encoders are happier with.
                add("-vf"); add("scale=trunc(iw*$scale/2)*2:-2:flags=lanczos")
            }
            add("-c:v"); add("libwebp_anim")
            add("-lossless"); add("0")
            add("-q:v"); add("80")
            add("-compression_level"); add("6")
            // Forever, which is what a showcase wants.
            add("-loop"); add("0")
            add(target.path)
        }
    ).redirectErrorStream(true).start()

    val log = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "ffmpeg failed for ${recording.name}:\n$log" }

    println(
        "${target.path}  ${recording.widthDp}x${recording.heightDp}dp  " +
            "${target.length() / 1024}KB"
    )
}
