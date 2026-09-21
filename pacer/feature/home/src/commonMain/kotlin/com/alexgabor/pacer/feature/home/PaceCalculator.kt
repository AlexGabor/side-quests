package com.alexgabor.pacer.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.Card
import com.alexgabor.design.riso.components.FlatButton
import com.alexgabor.design.riso.components.RisoAnimatedVisibility
import com.alexgabor.design.riso.components.track.TrackState
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.appstateurl.fakeAppUrlModule
import com.alexgabor.pacer.feature.home.PaceCalculatorState.Companion.launched
import com.alexgabor.pacer.feature.home.slider.DistanceSlider
import com.alexgabor.pacer.feature.home.slider.DistanceSliderState
import com.alexgabor.pacer.feature.home.slider.PaceSlider
import com.alexgabor.pacer.feature.home.slider.PaceSliderState
import com.alexgabor.pacer.feature.home.slider.TimeSlider
import com.alexgabor.pacer.feature.home.slider.TimeSliderState
import com.alexgabor.pacer.feature.home.slider.rememberDistanceSliderState
import com.alexgabor.pacer.feature.home.slider.rememberPaceSliderState
import com.alexgabor.pacer.feature.home.slider.rememberTimeSliderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplicationPreview
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Metric {
    Distance, Pace, Time
}

/**
 * The run being described: a distance, a pace and a time, any one of which is computed from the
 * other two.
 *
 * Distance and pace are held in kilometres whatever unit is on screen; see [Distance].
 */
class PaceCalculatorState(
    val distanceSliderState: DistanceSliderState = DistanceSliderState(),
    val paceSliderState: PaceSliderState = PaceSliderState(),
    val timeSliderState: TimeSliderState = TimeSliderState(),
    distance: Distance = DefaultDistance,
    pace: Duration = DefaultPace,
    time: Duration = DefaultTime,
    selectedMetric: Metric = Metric.Pace,
    selectedUnit: DistanceUnit = DistanceUnit.Kilometers,
) {
    var distance by mutableStateOf(distance)
        private set

    /** Per kilometre, whatever unit is on screen. */
    var pace by mutableStateOf(pace)
        private set

    var time by mutableStateOf(time)
        private set

    var selectedMetric by mutableStateOf(selectedMetric)
        private set

    var selectedUnit by mutableStateOf(selectedUnit)
        private set

    internal val distanceOnSlider: Double get() = distance.inUnit(selectedUnit)

    internal val paceOnSlider: Duration get() = pace * selectedUnit.kilometers

    internal val timeOnSlider: Duration get() = time

    /**
     * The card text, finer than the rulers: distance to the ten-thousandth, pace and time to the
     * hundredth of a second, trailing zeros dropped. Quantised from the exact values rather than from the rulers, and never
     * clamped to them — a ruler parked at its end still has the true figure written above it.
     */
    val displayedDistance: String
        get() = "${distanceOnSlider.tenThousandthsText()} ${selectedUnit.text}"

    val displayedPace: String
        get() {
            val hundredths = paceOnSlider.roundedHundredths()
            val seconds = hundredths / 100
            return "${seconds / 60}:${(seconds % 60).twoDigits()}" +
                    "${fractionText(hundredths % 100, digits = 2)} ${selectedUnit.paceText}"
        }

    val displayedTime: String
        get() {
            val hundredths = time.roundedHundredths()
            val seconds = hundredths / 100
            return "${seconds / 3600}h ${((seconds % 3600) / 60).twoDigits()}m " +
                    "${(seconds % 60).twoDigits()}${fractionText(hundredths % 100, digits = 2)}s"
        }

    /** What each card is headed with; also the first three lines of [shareText]. */
    internal val timeTitle: String get() = "Time = $displayedTime"

    internal val distanceTitle: String get() = "Distance = $displayedDistance"

    internal val paceTitle: String get() = "Pace = $displayedPace"

    /**
     * The run as someone else would be sent it: the card titles, in card order, and a link that
     * opens the web app on the same run — the same address it writes for itself as the run settles.
     */
    internal val shareText: String
        get() = listOf(
            timeTitle,
            distanceTitle,
            paceTitle,
            "$PacerWebUrl?${launchArgs.toLaunchParameters().toQueryString()}",
        ).joinToString("\n")

    fun selectMetric(metric: Metric) {
        if (metric == selectedMetric) return
        selectedMetric = metric
        recompute()
    }

    /**
     * Distance and pace are only ever "per unit" — the sliders carry no unit of their own — so a
     * change of unit is a change of the numbers on them, not just of the labels. Because the values
     * themselves are held in kilometres there is nothing to convert: the sliders read the new unit
     * and re-draw, and the run being described is untouched.
     */
    fun selectUnit(unit: DistanceUnit) {
        selectedUnit = unit
    }

    /** Counts preset taps, so each one moves the distance ruler even onto the value it shows. */
    private var presetSelections by mutableIntStateOf(0)

    /**
     * The same as scrolling the distance ruler to [preset]: the computed metric follows and the
     * other input is kept. The rulers catch up through [sync], and the address once the run settles.
     *
     * A fling still running on the distance ruler is let go of first, or it would carry on writing
     * its own distance over the preset; the ruler's move to the preset then stops it.
     */
    fun selectPreset(preset: DistancePreset) {
        if (selectedMetric == Metric.Distance) return
        distanceSliderState.interruptUserScroll()
        presetSelections++
        updateDistance(preset.distance)
        recompute()
    }

    /** Whichever metric is selected is the one computed; the other two are what the user sets. */
    private fun recompute() {
        when (selectedMetric) {
            // A pace of zero is infinite speed, and no distance follows from it.
            Metric.Distance -> if (pace > Duration.ZERO) updateDistance(Distance(time / pace))
            // And no pace follows from standing still.
            Metric.Pace -> if (distance.kilometers > 0.0) updatePace(time / distance.kilometers)
            Metric.Time -> updateTime(pace * distance.kilometers)
        }
    }

    /**
     * These keep non-finite values out of the fields but deliberately don't clamp: a value
     * past the end of its ruler stays exact, and the card shows all of it while the ruler parks.
     */
    private fun updateDistance(value: Distance) {
        if (value.kilometers.isFinite() && value.kilometers >= 0.0) distance = value
    }

    private fun updatePace(value: Duration) {
        if (value.isFinite() && value >= Duration.ZERO) pace = value
    }

    private fun updateTime(value: Duration) {
        if (value.isFinite() && value >= Duration.ZERO) time = value
    }

    internal fun onDistanceScrolled(displayed: Double) {
        if (selectedMetric == Metric.Distance) return
        updateDistance(Distance.of(displayed, selectedUnit))
        recompute()
    }

    internal fun onPaceScrolled(displayed: Duration) {
        if (selectedMetric == Metric.Pace) return
        updatePace(displayed / selectedUnit.kilometers)
        recompute()
    }

    internal fun onTimeScrolled(displayed: Duration) {
        if (selectedMetric == Metric.Time) return
        updateTime(displayed)
        recompute()
    }

    internal val tracks: List<TrackState<Int>>
        get() = distanceSliderState.tracks + paceSliderState.tracks + timeSliderState.tracks

    /** True while any slider is under the user's finger or still flinging from it. */
    internal val isUserScrolling: Boolean
        get() = distanceSliderState.isUserScrolling ||
                paceSliderState.isUserScrolling ||
                timeSliderState.isUserScrolling

    /**
     * This run as a launch would describe it — in the unit on screen, as [PacerLaunchArgs] is — so
     * that [launched] with it opens on the run being shown.
     *
     * All five are given, the computed one included: [launched] recomputes it from the other two
     * anyway, and naming the metric outright means it doesn't depend on which one was left out.
     */
    internal val launchArgs: PacerLaunchArgs
        get() = PacerLaunchArgs(
            distance = distanceOnSlider,
            pace = paceOnSlider,
            time = timeOnSlider,
            metric = selectedMetric,
            unit = selectedUnit,
        )

    /**
     * Keeps the values and the sliders showing the same run, in both directions.
     *
     * Lives with the state rather than with the layout so that reflowing the screen — rotating into
     * two panes, say — doesn't tear the sync down and re-animate all three sliders.
     */
    suspend fun sync(): Unit = coroutineScope {
        tracks.forEach { launch { it.trackUserScroll() } }

        launch {
            collectUserScroll(
                distanceSliderState::isUserScrolling,
                distanceSliderState::value,
                ::onDistanceScrolled,
                distanceSliderState::isInterrupted,
            )
        }
        launch {
            collectUserScroll(
                paceSliderState::isUserScrolling,
                paceSliderState::value,
                ::onPaceScrolled,
            )
        }
        launch {
            collectUserScroll(
                timeSliderState::isUserScrolling,
                timeSliderState::value,
                ::onTimeScrolled,
            )
        }

        launch {
            syncDown(
                ::distanceOnSlider,
                distanceSliderState::isUserScrolling,
                distanceSliderState::moveTo,
                ::presetSelections,
            )
        }
        launch {
            syncDown(::paceOnSlider, paceSliderState::isUserScrolling, paceSliderState::moveTo)
        }
        launch {
            syncDown(::timeOnSlider, timeSliderState::isUserScrolling, timeSliderState::moveTo)
        }
    }

    internal companion object {
        /**
         * A marathon at six minutes a kilometre. Consistent by hand: 42.20 km at 6:00/km is
         * exactly 4:13:12, so a default start needs no computing and cannot drift.
         */
        val DefaultDistance = Distance(42.20)
        val DefaultPace = 6.minutes
        val DefaultTime = 4.hours + 13.minutes + 12.seconds

        /**
         * The run [args] describes, made consistent.
         *
         * Whichever metric is selected is the computed one, so it is derived from the other two
         * even when the launch supplied a value for it — three independent numbers would otherwise
         * describe a run that doesn't add up. Which metric that is comes from [PacerLaunchArgs.metric]
         * when it was given, and otherwise from what was left out: naming exactly two of the three
         * reads as "work out the third".
         *
         * Defaults are left alone when nothing was launched with, because they are already
         * consistent and recomputing them would only round them.
         */
        fun launched(
            distanceSliderState: DistanceSliderState,
            paceSliderState: PaceSliderState,
            timeSliderState: TimeSliderState,
            args: PacerLaunchArgs,
        ): PaceCalculatorState {
            val unit = args.unit ?: DistanceUnit.Kilometers

            val selectedMetric = args.metric ?: when {
                args.distance == null && args.pace != null && args.time != null -> Metric.Distance
                args.pace == null && args.distance != null && args.time != null -> Metric.Pace
                args.time == null && args.distance != null && args.pace != null -> Metric.Time
                else -> Metric.Pace
            }

            return PaceCalculatorState(
                distanceSliderState,
                paceSliderState,
                timeSliderState,
                // Both are given in the unit on screen; the state holds kilometres.
                distance = args.distance?.let { Distance.of(it, unit) } ?: DefaultDistance,
                pace = args.pace?.let { it / unit.kilometers } ?: DefaultPace,
                time = args.time ?: DefaultTime,
                selectedMetric = selectedMetric,
                selectedUnit = unit,
            ).apply { if (!args.isEmpty) recompute() }
        }
    }
}

/**
 * Reports what the user's own finger did to a slider, and only that — a slider being driven
 * programmatically never emits a [androidx.compose.foundation.interaction.DragInteraction], so the
 * two directions can't chase each other.
 *
 * Reporting live rather than only once the gesture ends is what makes the other two cards move
 * under the finger. The falling edge is reported too: the settling scroll and the end of the
 * gesture can land in the same frame, and dropping that last value would leave the field one line
 * off the ruler — which the other direction would then correct as a visible snap-back.
 *
 * Except when the gesture was [isInterrupted]: then the ruler stopped wherever it was when something
 * else replaced its value, and reporting that would undo the replacement.
 */
internal suspend fun <T> collectUserScroll(
    isUserScrolling: () -> Boolean,
    value: () -> T,
    onValue: (T) -> Unit,
    isInterrupted: () -> Boolean = { false },
) {
    var touched = false
    snapshotFlow(isUserScrolling).collectLatest { scrolling ->
        if (scrolling) {
            touched = true
            snapshotFlow(value).collect(onValue)
        } else if (touched && !isInterrupted()) {
            onValue(value())
        }
    }
}

/**
 * Keeps the address describing the run, for as long as this is collected.
 *
 * Replaced rather than pushed: the user is adjusting one run, not visiting a series of them, and an
 * entry per adjustment would bury wherever they came from.
 *
 * @param appUrl read afresh each time, so an address that arrives late — or is swapped — is still
 * the one written to.
 */
internal suspend fun PaceCalculatorState.writeSettledRunsTo(appUrl: () -> AppUrl) {
    settledValues(::isUserScrolling, ::launchArgs).collect { run ->
        appUrl().replace(run.toLaunchParameters())
    }
}

/** How long the values have to sit still before [settledValues] reports them. */
internal val SettleDelay = 300.milliseconds

/**
 * Each value the calculator comes to rest on — once no slider is moving and nothing has changed for
 * [SettleDelay] — and nothing it passes through on the way.
 *
 * Settling is read off the gesture rather than the values: a slider is still moving through its
 * fling long after the finger has lifted, and a card tap moves nothing at all.
 *
 * The value it starts on is not reported; it is what the screen was opened with, so there is
 * nothing new to say about it. The delay is there for the frame in which the gesture has ended
 * but [collectUserScroll] has not yet written its last value, and so that a burst of taps is
 * reported once rather than per tap.
 */
@OptIn(FlowPreview::class)
internal fun <T> settledValues(
    isUserScrolling: () -> Boolean,
    value: () -> T,
): Flow<T> =
    snapshotFlow { isUserScrolling() to value() }
        .filterNot { (scrolling, _) -> scrolling }
        .map { (_, settled) -> settled }
        .distinctUntilChanged()
        .drop(1)
        .debounce(SettleDelay)

/**
 * Drives a slider to whatever its value has become.
 *
 * `collectLatest` rather than `collect`: while the user drags one card the other two change every
 * frame, and each change has to cancel the animation in flight rather than queue behind it.
 *
 * @param trigger moves the slider whenever it changes, even to the value it was already given — an
 * interrupted fling has to be taken over although the value it was replaced with may be the same.
 */
private suspend fun <T> syncDown(
    value: () -> T,
    isUserScrolling: () -> Boolean,
    moveTo: suspend (T, Boolean) -> Unit,
    trigger: () -> Any? = { null },
) {
    var placed = false
    snapshotFlow { value() to trigger() }.collectLatest { (target, _) ->
        if (isUserScrolling()) return@collectLatest
        try {
            // The first placement is the restored value arriving before anything is on screen, so
            // it jumps rather than animating a scroll the user never asked for.
            moveTo(target, placed)
            placed = true
        } catch (cancellation: CancellationException) {
            // A scroll that loses the mutex to the user's finger must not take the sync down with
            // it: a scroll animation started while a drag holds the scroll mutex at a higher
            // priority is cancelled outright, and a coroutine that ends that way ends quietly.
            currentCoroutineContext().ensureActive()
        }
    }
}

internal fun paceCalculatorStateSaver(
    distanceSliderState: DistanceSliderState,
    paceSliderState: PaceSliderState,
    timeSliderState: TimeSliderState,
): Saver<PaceCalculatorState, Any> = listSaver(
    save = { state ->
        listOf(
            state.distance.kilometers,
            // Milliseconds, not seconds: in miles the pace per kilometre isn't a whole number of
            // seconds, and rounding it on every rotation is the drift this all exists to avoid.
            state.pace.inWholeMilliseconds,
            state.time.inWholeMilliseconds,
            state.selectedMetric.name,
            state.selectedUnit.name,
        )
    },
    restore = { saved ->
        PaceCalculatorState(
            distanceSliderState,
            paceSliderState,
            timeSliderState,
            distance = Distance(saved[0] as Double),
            pace = (saved[1] as Long).milliseconds,
            time = (saved[2] as Long).milliseconds,
            // By name, and falling back rather than throwing, so a bundle written by an older
            // build with different entries degrades to a default.
            selectedMetric = Metric.entries.firstOrNull { it.name == saved[3] } ?: Metric.Pace,
            selectedUnit = DistanceUnit.entries.firstOrNull { it.name == saved[4] }
                ?: DistanceUnit.Kilometers,
        )
    },
)

/**
 * @param appUrl told about the run each time the calculator comes to rest on a new one — see
 * [settledValues] — so that the address of the page describes what is on it. Replaced rather than
 * pushed: a run is something the user is adjusting, not somewhere they went.
 */
@Composable
fun rememberPaceCalculatorState(
    args: PacerLaunchArgs = PacerLaunchArgs.None,
    appUrl: AppUrl = koinInject(),
): PaceCalculatorState {
    val distanceState = rememberDistanceSliderState()
    val paceState = rememberPaceSliderState()
    val timeState = rememberTimeSliderState()

    val saver = remember(distanceState, paceState, timeState) {
        paceCalculatorStateSaver(distanceState, paceState, timeState)
    }
    val state = rememberSaveable(distanceState, paceState, timeState, saver = saver) {
        PaceCalculatorState.launched(distanceState, paceState, timeState, args)
    }

    LaunchedEffect(state) { state.sync() }

    val currentAppUrl by rememberUpdatedState(appUrl)
    LaunchedEffect(state) { state.writeSettledRunsTo { currentAppUrl } }

    return state
}

@Composable
internal fun UnitSelector(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        selected = state.selectedUnit,
        *DistanceUnit.entries.toTypedArray(),
        modifier = modifier,
        onSelect = { state.selectUnit(it) },
    )
}

/**
 * One tap to a race distance, for the unit on screen. Plain headings rather than a button group:
 * the presets are actions, and none of them stays chosen once the rulers move on.
 *
 * Draws nothing while distance is the computed metric, for the same reason its ruler doesn't scroll.
 */
@Composable
internal fun DistancePresets(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
) {
    // The caller's modifier goes on the dissolve rather than on the row inside it: that is the
    // composable the caller's layout actually holds, and parent data — `Column.align`, here — is
    // only ever read by the layout a composable is a child of.
    RisoAnimatedVisibility(
        visible = state.selectedMetric != Metric.Distance,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DistancePreset.forUnit(state.selectedUnit).forEach { preset ->
                FlatButton(
                    text = preset.text,
                    onClick = { state.selectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun PresetsAndUnits(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(RisoTheme.dimens.screenPadding),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DistancePresets(state = state)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            UnitSelector(state = state)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slider: @Composable () -> Unit,
) {
    Card(
        isSelected = selected,
        modifier = modifier,
        onClick = onClick,
    ) {
        Column {
            Heading3(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
            )
            slider()
        }
    }
}

/**
 * widthIn before fillMaxWidth: constraints flow outwards in, so the fill has to resolve against the
 * already capped maximum. The other order would make the cap a no-op.
 */
@Composable
private fun cardModifier(maxCardWidth: Dp): Modifier =
    Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
        .widthIn(max = maxCardWidth)
        .fillMaxWidth()

/**
 * The three metric cards, so a caller can drop them into a list of its own.
 *
 * @param maxCardWidth caps how wide a card grows on a roomy window; a card is centred within
 * whatever is left over. [Dp.Unspecified] lets them fill the list.
 */
internal fun LazyListScope.metricCardItems(
    state: PaceCalculatorState,
    maxCardWidth: Dp = Dp.Unspecified,
) {
    item("time") {
        MetricCard(
            title = state.timeTitle,
            selected = state.selectedMetric != Metric.Time,
            onClick = { state.selectMetric(Metric.Time) },
            modifier = cardModifier(maxCardWidth),
        ) {
            TimeSlider(
                state = state.timeSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Time
            )
        }
    }

    item("distance") {
        MetricCard(
            title = state.distanceTitle,
            selected = state.selectedMetric != Metric.Distance,
            onClick = { state.selectMetric(Metric.Distance) },
            modifier = cardModifier(maxCardWidth),
        ) {
            DistanceSlider(
                state = state.distanceSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Distance
            )
        }
    }

    item("pace") {
        MetricCard(
            title = state.paceTitle,
            selected = state.selectedMetric != Metric.Pace,
            onClick = { state.selectMetric(Metric.Pace) },
            modifier = cardModifier(maxCardWidth),
        ) {
            PaceSlider(
                state = state.paceSliderState,
                userScrollEnabled = state.selectedMetric != Metric.Pace
            )
        }
    }
}

/** The cards on their own, for a layout that places the unit selector somewhere else. */
@Composable
internal fun MetricCards(
    state: PaceCalculatorState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = RisoTheme.dimens.screenPadding),
    maxCardWidth: Dp = Dp.Unspecified,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        metricCardItems(state, maxCardWidth)
    }
}

/** The unit selector and the cards in a single column. */
@Composable
internal fun PaceCalculator(
    modifier: Modifier = Modifier,
    state: PaceCalculatorState = rememberPaceCalculatorState(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = RisoTheme.dimens.screenPadding),
    maxCardWidth: Dp = Dp.Unspecified,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("unit") {
            PresetsAndUnits(
                state = state,
                modifier = Modifier.widthIn(max = maxCardWidth)
                    .fillMaxWidth()
                    .padding(horizontal = RisoTheme.dimens.screenPadding),
            )
        }
        metricCardItems(state, maxCardWidth)
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun PaceCalculatorPreview() {
    KoinApplicationPreview(application = { modules(fakeAppUrlModule) }) {
        RisoTheme {
            PaceCalculator(Modifier.background(RisoTheme.colors.paper))
        }
    }
}

/**
 * This number as two digits, zero-padded — `7` as `"07"`.
 *
 * The clock faces and the distance readout are all fixed-width, so a single-digit part has to carry
 * its own leading zero rather than let the text reflow around it. `String.format` would say the same
 * thing, but it is a JVM-only extension and this is read on iOS too.
 */
internal fun Int.twoDigits(): String = toString().padStart(2, '0')

internal fun Long.twoDigits(): String = toString().padStart(2, '0')
