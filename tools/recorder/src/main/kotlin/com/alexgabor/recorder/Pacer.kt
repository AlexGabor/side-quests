package com.alexgabor.recorder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.alexgabor.pacer.slider.DistanceUnit
import com.alexgabor.pacer.slider.Metric
import com.alexgabor.pacer.slider.PaceCalculator
import com.alexgabor.pacer.slider.PaceCalculatorState
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How wide the calculator is recorded. Roomy enough that a ruler shows three or four lines. */
private val Width = 420.dp

/** 25 rather than 50: this take is long and tall, and a ruler reads fine at 25. */
private const val Rate = 25

/**
 * Rendered at one and a half pixels per dp rather than the usual two.
 *
 * This is by far the biggest sheet — a whole screen's worth of cards, each an ink pass of its own —
 * and every one of those passes is a shader run over every pixel on the CPU. Two thirds of the
 * pixels is a third off the render and off the file, and a README shows it at 1.5x anyway.
 */
private const val PacerDensity = 1.5f

/**
 * The calculator, run through a round trip of edits that lands exactly where it started.
 *
 * A marathon at 6:00 min/km, solved for pace. Time is dragged to 3:25, then Time is selected and
 * pace dragged to 5:41, then the units go to miles and back, and pace is walked back to 6:00 — which
 * puts the time back to 4:13:12 to the second, because 360s x 42.2km is exactly that. Selecting Pace
 * again recomputes the same 6:00, so the last frame is the first one and the loop closes.
 *
 * Every value moves through the pointer rather than through the state. `collectUserScroll` only
 * reports a slider the user's own finger moved — a programmatic scroll would slide one ruler and
 * leave the other two behind, which is the opposite of what this is meant to show.
 */
fun recordPacer(into: File): Recording {
    val state = PaceCalculatorState()
    val listState = LazyListState()

    val height = measureHeight()
    var director: Director? = null

    return record(
        name = "pacer",
        into = into,
        maxFrames = 1500,
        rate = Rate,
        density = PacerDensity,
        // A finger: the rulers are lazy rows, and a lazy row will not be dragged by a mouse.
        pointerType = PointerType.Touch,
        size = DpSize(Width, height),
        // Long enough for the last card's selection spring to finish, so the loop closes on a
        // settled frame.
        tail = Rate,
        content = { Calculator(state, listState) },
    ) { _ ->
        // Built on the first frame rather than up front: the choreography is laid out around where
        // the rulers and cards actually ended up, which is only knowable once there is a scene.
        val steps = director ?: Director(script(state, this)).also { director = it }
        steps.advance(this)
    }
}

/**
 * The height the calculator's own items add up to.
 *
 * A lazy list has no height to ask for — it takes whatever it is given — so it is composed once at a
 * generous height and asked what it actually laid out. Recording that height is what keeps the list
 * from scrolling and keeps a strip of empty paper out of the frame.
 */
private fun measureHeight(): Dp {
    val listState = LazyListState()
    val state = PaceCalculatorState()
    return probe(
        size = DpSize(Width, 1000.dp),
        density = PacerDensity,
        content = { Calculator(state, listState) },
    ) {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.last()
        check(last.index == info.totalItemsCount - 1) { "The calculator did not fit the probe" }
        ((last.offset + last.size + info.afterContentPadding) / PacerDensity).dp
    }
}

/**
 * The calculator, with the sync running.
 *
 * Not `rememberPaceCalculatorState()`: the state has to be reachable from outside the composition
 * for the take to read what the rulers say, and starting the sync is what that function does over
 * and above holding one.
 */
@Composable
private fun Calculator(state: PaceCalculatorState, listState: LazyListState) {
    LaunchedEffect(state) { state.sync() }
    PaceCalculator(
        modifier = Modifier.fillMaxSize(),
        state = state,
        listState = listState,
    )
}

/**
 * The choreography, resolved against where things actually are on screen.
 *
 * The seven rulers come off the semantics tree in layout order — time's hours, minutes and seconds,
 * distance's whole and fraction, pace's minutes and seconds — and the cards and unit segments the
 * same way. Nothing here counts paddings.
 */
private fun script(state: PaceCalculatorState, take: Take): List<Step> {
    val tracks = take.nodesWhere {
        it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
    }.sortedBy { it.top }
    check(tracks.size == 7) { "Expected seven rulers, found ${tracks.size}" }

    // The cards are the only clickable without a role; the unit segments are radio buttons.
    val cards = take.nodesWhere {
        it.config.getOrNull(SemanticsActions.OnClick) != null &&
            it.config.getOrNull(SemanticsProperties.Role) == null
    }.sortedBy { it.top }
    check(cards.size == 3) { "Expected three cards, found ${cards.size}" }

    val units = take.segments()
    check(units.size == 2) { "Expected two unit segments, found ${units.size}" }

    val time = { state.timeSliderState.value }
    val pace = { state.paceSliderState.value }
    val timeScrolling = { state.timeSliderState.isUserScrolling }
    val paceScrolling = { state.paceSliderState.isUserScrolling }

    fun timeDrag(ruler: Int, dpPerTick: Float, target: Int, tick: (Duration) -> Int, label: String) =
        Drag(tracks[ruler], dpPerTick, target, { tick(time()) }, timeScrolling, label)

    fun paceDrag(ruler: Int, dpPerTick: Float, target: Int, tick: (Duration) -> Int, label: String) =
        Drag(tracks[ruler], dpPerTick, target, { tick(pace()) }, paceScrolling, label)

    val hours = { value: Duration -> value.inWholeHours.toInt() }
    val minutes = { value: Duration -> (value.inWholeMinutes % 60).toInt() }
    val seconds = { value: Duration -> (value.inWholeSeconds % 60).toInt() }

    return listOf(
        Dwell(Rate / 2),

        // Time to 3:25:00. Pace is the selected metric, so it is the one that follows.
        timeDrag(0, 150f, 3, hours, "time hours"),
        Settle,
        timeDrag(1, 100f, 25, minutes, "time minutes"),
        Settle,
        timeDrag(2, 20f, 0, seconds, "time seconds"),
        Settle,
        Dwell(6),

        // Solve for time instead, then set the pace.
        Tap(cards[0], "Time card"),
        Settle,
        Dwell(6),
        paceDrag(5, 100f, 5, minutes, "pace minutes"),
        Settle,
        paceDrag(6, 20f, 41, seconds, "pace seconds"),
        Settle,
        Dwell(6),

        // The same run in miles, and back.
        Tap(units[1], "mi"),
        Settle,
        Dwell(Rate),
        Tap(units[0], "km"),
        Settle,
        Dwell(6),

        // Pace back to 6:00, which puts the time back to 4:13:12 exactly.
        paceDrag(5, 100f, 6, minutes, "pace minutes"),
        Settle,
        paceDrag(6, 20f, 0, seconds, "pace seconds"),
        Settle,
        Dwell(6),

        // Solve for pace again: 15192s / 42.2km is the 6:00 it started on.
        Tap(cards[2], "Pace card"),
        Settle,
        Dwell(6),
        Verify(state),
        Exit,
    )
}

/**
 * Waits for the app to stop moving.
 *
 * Between every step, because the choreography must not interrupt the app's own animations: a scroll
 * the sync started, cancelled halfway by the next tap, leaves its ruler stranded where it stopped and
 * nothing ever puts it right — the value it was chasing has not changed since. It is also what makes
 * the last frame match the first, the card borders being springs with a long tail.
 */
private object Settle : Step {
    /** Frames of quiet before this is believed. */
    private const val Quiet = 3

    private const val Longest = 150

    private var still = 0
    private var elapsed = 0

    override fun advance(take: Take): Boolean {
        if (elapsed++ == 0) still = 0
        still = if (take.settled) still + 1 else 0
        if (still >= Quiet || elapsed >= Longest) {
            elapsed = 0
            return false
        }
        return true
    }
}

/**
 * The last frame is the first one, or this take is not worth having.
 *
 * Checked rather than eyeballed: everything here comes back to where it started by arithmetic — the
 * pace is walked back to a round 6:00 and the time follows from it exactly — so anything off by a
 * second is a bug in the driving, not a rounding that has to be lived with.
 */
private class Verify(private val state: PaceCalculatorState) : Step {
    override fun advance(take: Take): Boolean {
        check(state.timeSliderState.value == 4.hours + 13.minutes + 12.seconds) {
            "Time came back as ${state.timeSliderState.value}"
        }
        check(state.paceSliderState.value == 6.minutes) {
            "Pace came back as ${state.paceSliderState.value}"
        }
        check(state.distanceSliderState.value == 42.20) {
            "Distance came back as ${state.distanceSliderState.value}"
        }
        check(state.selectedMetric == Metric.Pace) { "Selected ${state.selectedMetric}" }
        check(state.selectedUnit == DistanceUnit.Kilometers) { "Unit ${state.selectedUnit}" }
        println("  back where it started")
        return false
    }
}

/** Runs the steps one after another, a frame at a time. */
private class Director(private val steps: List<Step>) {
    private var index = 0

    /** Advances one frame. False once there is nothing left to do. */
    fun advance(take: Take): Boolean {
        val step = steps.getOrNull(index) ?: return false
        if (!step.advance(take)) index++
        return index < steps.size
    }
}

/** One thing to do, spread over as many frames as it needs. Returns false when it is finished. */
private interface Step {
    fun advance(take: Take): Boolean
}

/** Nothing, for a beat. */
private class Dwell(private val frames: Int) : Step {
    private var elapsed = 0
    override fun advance(take: Take): Boolean = elapsed++ < frames
}

/** Takes the cursor off the sheet, so the last frame matches the first. */
private object Exit : Step {
    override fun advance(take: Take): Boolean {
        take.exit()
        return false
    }
}

/** How long the cursor takes to travel to whatever it is about to use. */
private const val TravelFrames = 4

/**
 * Frames held still before a release, so the velocity tracker reads a stop rather than a fling.
 *
 * Four, because the tracker fits a curve through the last hundred milliseconds and that is what a
 * hundred milliseconds is at this take's frame rate. Fewer, and a stroke that means to place a ruler
 * on a line releases with speed still in it, flies a line past, and the stroke sent to correct that
 * does the same in the other direction — the two of them trading the ruler back and forth forever.
 */
private const val HoldFrames = 4

private fun ease(t: Float): Float = t * t * (3f - 2f * t)

private fun lerp(from: Offset, to: Offset, fraction: Float): Offset =
    from + (to - from) * fraction.coerceIn(0f, 1f)

/** A press and a release on [target], with the cursor travelling there first. */
private class Tap(private val target: Rect, private val label: String) : Step {
    private var elapsed = 0
    private var from: Offset? = null
    private var travel = 0

    override fun advance(take: Take): Boolean {
        val to = target.center
        if (elapsed == 0) {
            if (take.shown) {
                from = take.position
                travel = TravelFrames
            } else {
                take.enter(to)
            }
        }

        val start = from
        when {
            start != null && elapsed in 1..travel ->
                take.moveTo(lerp(start, to, ease(elapsed.toFloat() / travel)))

            elapsed == travel + 1 -> take.press()
            elapsed == travel + 1 + HoldFrames -> {
                take.release()
                println("  tapped $label")
            }

            elapsed >= travel + 1 + 2 * HoldFrames -> return false
        }
        elapsed++
        return true
    }
}

/**
 * Drags a ruler until it reads [target], however many strokes that takes.
 *
 * Closed loop, because the rulers are long: time's minute ruler is 100.dp a minute, so 4:13 to 3:25
 * is twelve hundred dp of travel and the card is four hundred wide. A stroke that cannot reach the
 * target is released while still moving, and
 * [SubdivisionFlingBehavior][com.alexgabor.pacer.track.SubdivisionFlingBehavior] carries it on —
 * anything above 800px/s decays — while one that can reach it eases to a stop first, so the snap at
 * the end of the fling lands on the line asked for rather than the one next to it. Whatever a stroke
 * left over, including an overshoot, is simply what the next stroke has to undo.
 */
private class Drag(
    private val track: Rect,
    private val dpPerTick: Float,
    private val target: Int,
    private val tick: () -> Int,
    private val scrolling: () -> Boolean,
    private val label: String,
) : Step {

    private enum class Phase { Plan, Travel, Stroke, Hold, Settle }

    private var phase = Phase.Plan
    private var elapsed = 0
    private var strokes = 0
    private var from = Offset.Zero
    private var to = Offset.Zero
    private var travelFrom: Offset? = null
    private var duration = 0
    private var fling = false

    /**
     * One frame of the stroke, whichever part of it is in hand.
     *
     * Every branch returns for itself. A phase change costs the rest of its frame rather than
     * sharing it with the phase it hands over to, which keeps a press from landing before the
     * cursor has arrived where it means to press.
     */
    override fun advance(take: Take): Boolean {
        when (phase) {
            Phase.Plan -> {
                val remaining = target - tick()
                if (remaining == 0) {
                    println("  dragged $label to $target after $strokes stroke(s)")
                    return false
                }
                check(strokes++ < MaxStrokes) { "$label never reached $target, stuck at ${tick()}" }

                // A rising value means the ruler moves left under the finger.
                val wanted = -remaining * take.dp(dpPerTick)
                val inset = take.dp(Inset)
                val usable = track.width - 2 * inset
                val distance = min(abs(wanted), usable)
                fling = abs(wanted) > usable
                val direction = if (wanted < 0) -1f else 1f
                val grabX = if (direction < 0) track.right - inset else track.left + inset

                from = Offset(grabX, track.center.y)
                // Plus the slop the gesture eats before the ruler starts moving at all, capped
                // short of half a line so that compensating for it can never carry the snap onto
                // the wrong one.
                val slop = min(take.dp(Slop), take.dp(dpPerTick) * 0.4f)
                to = Offset(grabX + direction * (distance + slop), track.center.y)
                // Both speeds are in dp a frame, so a take recorded at another density moves the
                // ruler at the same speed on screen.
                duration = if (fling) {
                    (distance / take.dp(FlingSpeed)).roundToInt().coerceAtLeast(3)
                } else {
                    (distance / take.dp(DragSpeed)).roundToInt().coerceAtLeast(5)
                }
                phase = Phase.Travel
                elapsed = 0
                return true
            }

            Phase.Travel -> {
                if (elapsed == 0 && !take.shown) take.enter(from)
                if (elapsed == 0 && take.shown) travelFrom = take.position
                elapsed++
                val start = travelFrom
                if (start != null && elapsed < TravelFrames) {
                    take.moveTo(lerp(start, from, ease(elapsed.toFloat() / TravelFrames)))
                    return true
                }
                take.moveTo(from)
                take.press()
                travelFrom = null
                phase = Phase.Stroke
                elapsed = 0
                return true
            }

            Phase.Stroke -> {
                elapsed++
                val t = (elapsed.toFloat() / duration).coerceAtMost(1f)
                // A fling is released at speed, so it is dragged at a constant one; a stroke that
                // means to stop where it is eases down to nothing first.
                take.moveTo(lerp(from, to, if (fling) t else ease(t)))
                if (elapsed >= duration) {
                    if (fling) {
                        take.release()
                        phase = Phase.Settle
                    } else {
                        phase = Phase.Hold
                    }
                    elapsed = 0
                }
                return true
            }

            Phase.Hold -> {
                // Still, but still reporting: the velocity tracker averages the last few samples, and
                // these are the ones that bring it down to zero.
                elapsed++
                take.moveTo(to)
                if (elapsed >= HoldFrames) {
                    take.release()
                    phase = Phase.Settle
                    elapsed = 0
                }
                return true
            }

            Phase.Settle -> {
                elapsed++
                // Both the ruler and the whole scene: the fling and its snap end the user's own
                // scroll, but the sync then animates every other ruler to match — and one of those
                // animations is on this very slider, since the value it is chasing came off it. A
                // stroke started while that is still running lands on top of it, and the ruler moves
                // further than the finger did.
                if (elapsed >= 2 && !scrolling() && take.settled) {
                    phase = Phase.Plan
                    elapsed = 0
                }
                return true
            }
        }
    }

    private companion object {
        /** How far in dp from a ruler's edge the finger lands, so a stroke has room to run. */
        const val Inset = 24f

        /**
         * The drag slop, in dp: what a gesture spends proving it is a drag before the ruler moves.
         * Compose's own default is 18px, which is this at the density the take records at.
         */
        const val Slop = 12f

        /** Dp a frame while dragging to a stop — about 325.dp a second at 25fps. */
        const val DragSpeed = 13f

        /** Dp a frame while flinging: comfortably past the 800px/s the decay needs. */
        const val FlingSpeed = 40f

        const val MaxStrokes = 12
    }
}
