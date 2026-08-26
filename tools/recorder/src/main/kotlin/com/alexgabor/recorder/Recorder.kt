package com.alexgabor.recorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import kotlin.math.roundToInt

/**
 * Frames per second, unless a take asks for another rate.
 *
 * 20ms a frame divides evenly, where 60fps' 16.67ms does not — a WebP frame's duration is whole
 * milliseconds. Every rate a take picks should divide 1000 for the same reason.
 */
const val FrameRate = 50

/**
 * Pixels per dp a take is rendered at unless it asks for another.
 *
 * More than one, so the recordings stay sharp on the retina displays a README is read on; the README
 * then divides them back down to show the component at its true size. It is also the whole cost of a
 * take — every ink pass is a shader run over this many pixels, on the CPU — so a big one is recorded
 * at less.
 */
const val RecordingDensity = 2f

/** The paper showing around the component, on every side. */
private val Margin = 32.dp

/** One finished take: where its frames are, how big it came out, and how fast it plays. */
class Recording(
    val name: String,
    val frames: File,
    val size: IntSize,
    val rate: Int,
    val density: Float,
) {
    /** The component's own size, margins included, as it should be shown. */
    val widthDp: Int get() = (size.width / density).toInt()
    val heightDp: Int get() = (size.height / density).toInt()
}

/**
 * Renders [content] to a folder of PNG frames, with [script] driving the pointer.
 *
 * The scene is sized to the content rather than the other way around: it is measured first, then a
 * second scene is opened at exactly that size, because [ImageComposeScene] fixes its surface at
 * construction. What lands in the frame is the component, [Margin] of paper around it, and nothing
 * else. A [size] given outright skips the measuring — a lazy layout has no size of its own to ask
 * for, and measuring one against unbounded constraints throws.
 *
 * [script] runs once per frame before that frame is rendered, so a pointer event sent on frame *n*
 * is visible from frame *n* on. It returns whether there is more to come: a take that drives an app
 * until it settles does not know its own length up front, so [maxFrames] is a guard rather than a
 * length, and [tail] frames are recorded after the script says it is done.
 */
fun record(
    name: String,
    into: File,
    maxFrames: Int,
    rate: Int = FrameRate,
    density: Float = RecordingDensity,
    pointerType: PointerType = PointerType.Mouse,
    size: DpSize? = null,
    tail: Int = 0,
    showCursor: Boolean = true,
    content: @Composable () -> Unit,
    script: Take.(frame: Int) -> Boolean,
): Recording {
    val sheet = size?.let {
        IntSize(
            it.width.toPx(density) + Margin.toPx(density) * 2,
            it.height.toPx(density) + Margin.toPx(density) * 2,
        )
    } ?: measure(content, density)

    val pointer = Pointer()
    val dispatcher = QueueDispatcher()
    val scene = ImageComposeScene(
        width = sheet.width,
        height = sheet.height,
        density = Density(density),
        coroutineContext = dispatcher,
        content = { Sheet(cursor = pointer.takeIf { showCursor }, size = size, content = content) },
    )

    val folder = into.resolve(name).apply {
        deleteRecursively()
        mkdirs()
    }

    var frames = 0
    scene.use {
        dispatcher.settle(scene)
        val take = Take(scene, pointer, sheet, rate, density, pointerType)
        var running = true
        var left = tail
        while (frames < maxFrames) {
            take.frame = frames
            if (running) running = take.script(frames) else if (left-- <= 0) break
            dispatcher.drain()
            val image = scene.render(frames * (1_000_000_000L / rate))
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                folder.resolve("frame_%04d.png".format(frames)).writeBytes(data.bytes)
            }
            frames++
        }
        // On the guard rather than on the frame count: a take is allowed to end on the last frame
        // it is allowed, and only one that was still going when it ran out has gone wrong.
        check(!running) { "$name never finished: hit the $maxFrames frame guard" }
    }

    return Recording(name, folder, sheet, rate, density)
}

/**
 * Settles a scene holding [content] at the given size and reads something off it with [read].
 *
 * For what a take needs to know before it can frame itself and cannot ask for statically — the
 * height a lazy layout's items actually add up to, say. The scene is generous and thrown away; only
 * what [read] pulls out of the composition survives it.
 */
fun <T> probe(
    size: DpSize,
    density: Float = RecordingDensity,
    content: @Composable () -> Unit,
    read: () -> T,
): T {
    val dispatcher = QueueDispatcher()
    return ImageComposeScene(
        width = size.width.toPx(density),
        height = size.height.toPx(density),
        density = Density(density),
        coroutineContext = dispatcher,
        content = { Sheet(cursor = null, size = size, content = content) },
    ).use { scene ->
        dispatcher.settle(scene)
        read()
    }
}

/**
 * The size [content] wants, margins included.
 *
 * On its own scene, and without the cursor: the cursor is drawn over the sheet rather than beside
 * the content, but measuring what the component alone asks for is the one thing that decides the
 * frame, and it should not be able to depend on anything the recorder draws.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun measure(content: @Composable () -> Unit, density: Float): IntSize {
    val dispatcher = QueueDispatcher()
    return ImageComposeScene(
        width = 2000,
        height = 1200,
        density = Density(density),
        coroutineContext = dispatcher,
        content = { Sheet(cursor = null, size = null, content = content) },
    ).use { scene ->
        // After settling, not before: the label is laid out in a fallback face until the Fira Code
        // resource loads, and the button is as wide as its label.
        dispatcher.settle(scene)
        scene.calculateContentSize()
    }
}

@Composable
private fun Sheet(cursor: Pointer?, size: DpSize?, content: @Composable () -> Unit) {
    RisoTheme {
        Box(modifier = Modifier.risoPaper()) {
            Box(
                modifier = Modifier.padding(Margin)
                    .then(if (size != null) Modifier.size(size) else Modifier)
            ) {
                content()
            }
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
    private val rate: Int,
    /** Pixels per dp this take is rendered at. */
    val density: Float,
    /**
     * What is doing the pointing.
     *
     * It decides more than which events carry hover: `Modifier.scrollable` refuses to be dragged by
     * a mouse at all — that is what a wheel is for — so anything that means to pull a list about has
     * to say it is a finger.
     */
    private val pointerType: PointerType,
) {
    internal var frame: Int = 0

    /** Where the pointer is now, in scene pixels. */
    val position: Offset get() = pointer.position

    /** Whether the pointer is on the sheet at all. */
    val shown: Boolean get() = pointer.shown

    /**
     * True when nothing is left to recompose, lay out, draw or animate.
     *
     * What a take waits on before it touches the app again: an animation cancelled halfway leaves
     * whatever it was moving stranded where it stopped, and a scroll the sync started is exactly
     * that kind of animation.
     */
    val settled: Boolean get() = !scene.hasInvalidations()

    fun dp(value: Float): Float = value * density

    fun enter(at: Offset) {
        pointer.position = at
        pointer.shown = true
        // A finger does not hover, so a touch take only brings its cursor into view here; the first
        // thing the content hears from it is the press.
        if (pointerType == PointerType.Mouse) send(PointerEventType.Enter, at)
    }

    fun moveTo(at: Offset) {
        pointer.position = at
        if (pointerType == PointerType.Mouse || pointer.pressed) send(PointerEventType.Move, at)
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
        if (pointerType == PointerType.Mouse) send(PointerEventType.Exit, pointer.position)
        pointer.shown = false
    }

    /**
     * The bounds of every node the predicate accepts, in scene pixels.
     *
     * Asking the semantics tree where things are, rather than counting paddings: what a take has to
     * hit is a segment as wide as its label or a ruler somewhere down a lazy list, and both move
     * whenever the thing being recorded changes.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun nodesWhere(predicate: (SemanticsNode) -> Boolean): List<Rect> {
        // The unmerged tree: a clickable merges what is inside it, and the rulers a take has to
        // drag live inside a clickable card.
        val root = scene.semanticsOwners.firstOrNull()?.unmergedRootSemanticsNode
            ?: return emptyList()
        val found = mutableListOf<Rect>()
        fun walk(node: SemanticsNode) {
            if (predicate(node)) found += node.boundsInRoot
            node.children.forEach(::walk)
        }
        walk(root)
        return found
    }

    /**
     * The bounds of a `ButtonGroup`'s segments, left to right.
     *
     * The group gives every segment `Role.RadioButton`, and the segments are as wide as their
     * labels — so this is the only way to hit their centres that stays right when the labels change.
     */
    fun segments(): List<Rect> =
        nodesWhere { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
            .sortedBy { it.left }

    private fun send(type: PointerEventType, at: Offset, button: PointerButton? = null) {
        scene.sendPointerEvent(
            eventType = type,
            position = at,
            // The take's own clock, not the wall clock. A frame of this takes the better part of a
            // second to render, and a velocity tracker fed real timestamps would read every fling as
            // a dead stop.
            timeMillis = frame * 1000L / rate,
            type = pointerType,
            button = button,
        )
    }
}

private fun Dp.toPx(density: Float): Int = (value * density).roundToInt()

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
    fun settle(scene: ImageComposeScene, timeoutMillis: Long = 30_000) {
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
