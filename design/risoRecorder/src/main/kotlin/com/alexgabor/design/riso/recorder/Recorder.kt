package com.alexgabor.design.riso.recorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/** Frames per second. 20ms a frame divides evenly, where 60fps' 16.67ms does not — see [Encoder]. */
const val FrameRate = 50

private const val FrameNanos = 1_000_000_000L / FrameRate

/**
 * Pixels per dp the takes are rendered at. Two, so the recordings stay sharp on the retina displays
 * a README is read on; the README then halves them back to show the components at their true size.
 */
const val RecordingDensity = 2f

/** The paper showing around the component, on every side. */
private val Margin = 32.dp

/** One finished take: where its frames are, and how big the component came out. */
class Recording(val name: String, val frames: File, val size: IntSize) {
    /** The component's own size, margins included, as it should be shown. */
    val widthDp: Int get() = (size.width / RecordingDensity).toInt()
    val heightDp: Int get() = (size.height / RecordingDensity).toInt()
}

/**
 * Renders [content] to a folder of PNG frames, with [script] driving the pointer.
 *
 * The scene is sized to the content rather than the other way around: it is measured first, then a
 * second scene is opened at exactly that size, because [ImageComposeScene] fixes its surface at
 * construction. What lands in the frame is the component, [Margin] of paper around it, and nothing
 * else.
 *
 * [script] runs once per frame before that frame is rendered, so a pointer event sent on frame *n*
 * is visible from frame *n* on.
 */
fun record(
    name: String,
    frames: Int,
    into: File,
    showCursor: Boolean = true,
    content: @Composable () -> Unit,
    script: Take.(frame: Int) -> Unit,
): Recording {
    val size = measure(content)

    val pointer = Pointer()
    val dispatcher = QueueDispatcher()
    val scene = ImageComposeScene(
        width = size.width,
        height = size.height,
        density = Density(RecordingDensity),
        coroutineContext = dispatcher,
        content = { Sheet(cursor = pointer.takeIf { showCursor }, content = content) },
    )

    val folder = into.resolve(name).apply {
        deleteRecursively()
        mkdirs()
    }

    scene.use {
        dispatcher.settle(scene)
        val take = Take(scene, pointer, size)
        repeat(frames) { frame ->
            take.script(frame)
            dispatcher.drain()
            val image = scene.render(frame * FrameNanos)
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                folder.resolve("frame_%04d.png".format(frame)).writeBytes(data.bytes)
            }
        }
    }

    return Recording(name, folder, size)
}

/**
 * The size [content] wants, margins included.
 *
 * On its own scene, and without the cursor: the cursor is drawn over the sheet rather than beside
 * the content, but measuring what the component alone asks for is the one thing that decides the
 * frame, and it should not be able to depend on anything the recorder draws.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun measure(content: @Composable () -> Unit): IntSize {
    val dispatcher = QueueDispatcher()
    return ImageComposeScene(
        width = 2000,
        height = 1200,
        density = Density(RecordingDensity),
        coroutineContext = dispatcher,
        content = { Sheet(cursor = null, content = content) },
    ).use { scene ->
        // After settling, not before: the label is laid out in a fallback face until the Fira Code
        // resource loads, and the button is as wide as its label.
        dispatcher.settle(scene)
        scene.calculateContentSize()
    }
}

@Composable
private fun Sheet(cursor: Pointer?, content: @Composable () -> Unit) {
    RisoTheme {
        Box(modifier = Modifier.risoPaper()) {
            Box(modifier = Modifier.padding(Margin)) { content() }
            // matchParentSize, so the cursor is sized by the sheet after the content has decided how
            // big the sheet is, and never grows the frame. Outside the margin's padding as well, so
            // it can be positioned in the scene's own coordinates.
            cursor?.let { Cursor(it, Modifier.matchParentSize()) }
        }
    }
}

/** What a script can do to a scene between two frames. */
class Take internal constructor(
    private val scene: ImageComposeScene,
    private val pointer: Pointer,
    val size: IntSize,
) {
    fun dp(value: Float): Float = value * RecordingDensity

    fun enter(at: Offset) {
        pointer.position = at
        pointer.shown = true
        send(PointerEventType.Enter, at)
    }

    fun moveTo(at: Offset) {
        pointer.position = at
        send(PointerEventType.Move, at)
    }

    fun press() {
        pointer.pressed = true
        send(PointerEventType.Press, pointer.position, PointerButton.Primary)
    }

    fun release() {
        pointer.pressed = false
        send(PointerEventType.Release, pointer.position, PointerButton.Primary)
    }

    fun exit() {
        send(PointerEventType.Exit, pointer.position)
        pointer.shown = false
    }

    /**
     * The bounds of the group's segments, left to right, read off the semantics tree.
     *
     * `ButtonGroup` gives every segment `Role.RadioButton`, and the segments are as wide as their
     * labels — so this is the only way to hit their centres that stays right when the labels change.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun segments(): List<Rect> {
        val root = scene.semanticsOwners.firstOrNull()?.rootSemanticsNode ?: return emptyList()
        val found = mutableListOf<Rect>()
        fun walk(node: SemanticsNode) {
            if (node.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton) {
                found += node.boundsInRoot
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return found.sortedBy { it.left }
    }

    private fun send(type: PointerEventType, at: Offset, button: PointerButton? = null) {
        scene.sendPointerEvent(
            eventType = type,
            position = at,
            type = PointerType.Mouse,
            button = button,
        )
    }
}

/**
 * The dispatcher every effect in the scene runs on.
 *
 * A scene is single threaded, and the sheet does not settle on the thread that renders it: the paper
 * bake waits out its settle delay and then runs on a worker, and the font resource loads off-thread
 * too. Left on the default unconfined dispatcher both of those would resume wherever they happened
 * to finish. Queued here instead, and drained by the recorder between frames, they land on the one
 * thread that touches the scene.
 */
private class QueueDispatcher : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }

    fun drain() {
        while (true) (queue.poll() ?: return).run()
    }

    /**
     * Renders until the scene stops changing.
     *
     * Both of the slow arrivals — the baked sheet and the font — are real-time waits rather than
     * frame-clock ones, so this is wall clock and not the take's own timeline: it renders frame zero
     * over and over until two of them come out identical, which is the point where everything the
     * scene was waiting for has landed and frame zero of the take is what it will be.
     */
    fun settle(scene: ImageComposeScene, timeoutMillis: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var previous: ByteArray? = null
        var stable = 0
        while (System.currentTimeMillis() < deadline) {
            drain()
            val pixels = scene.render(0).encodeToData(EncodedImageFormat.PNG)?.bytes
            stable = if (pixels != null && previous != null && pixels.contentEquals(previous)) {
                stable + 1
            } else {
                0
            }
            previous = pixels
            // Three in a row rather than two: the bake settles for 150ms before it even starts, and
            // a pair of identical frames inside that window says nothing.
            if (stable >= 3 && !scene.hasInvalidations()) return
            Thread.sleep(40)
        }
        error("Scene never settled")
    }
}
