package com.alexgabor.design.riso.risograph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.LocalPress
import com.alexgabor.design.riso.attributes.NamedInk
import com.alexgabor.design.riso.attributes.Press
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.attributes.RisoPress
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.risograph.inks.onRisoPaper
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.inks.risoOverprint
import com.alexgabor.design.riso.risograph.inks.RisoInk
import com.alexgabor.design.riso.risograph.paper.RisoPaper
import com.alexgabor.design.riso.risograph.paper.risoPaper
import com.alexgabor.design.riso.risograph.region.risoBypass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Tint steps down each axis of the mixer grid, as on a press's tint scale. */
private const val GRID_STEPS = 10

/** How far two neighbouring Venn circles sit apart, in radii. */
private const val VENN_SPREAD = 0.62f

/**
 * How many inks the chart demonstrates. A mixer chart has one axis per ink and only two of them,
 * plus a rising third, so three is all it can hold — the press still carries the whole rack.
 */
private const val CHART_INKS = 3

/**
 * Playground for [risoPaper] and [risoInk]: load any of the RISO inks onto the press and see how they
 * mix, over a full set of controls for the press and the sheet.
 *
 * The two are configured separately because they are separate things. The press — which drums are
 * loaded, how hard they screen and mottle — is house style and goes on the theme, so every composable
 * on screen prints on the press being configured. The sheet is per-call, and goes on the modifier.
 */
@Preview
@Composable
fun RisoPrintDemo(modifier: Modifier = Modifier) {
    val palette = RisoColors.inks.all
    // The whole rack is loaded, always. It costs nothing: a composable prints on the drums it names,
    // and nothing here walks the rack per pixel. Picking a swatch chooses what the chart ramps
    // against, not what the press is carrying.
    var press by remember { mutableStateOf(RisoPress) }
    var paper by remember { mutableStateOf(RisoPaper()) }
    // Pink, yellow and blue: the subtractive triad a printed mixer chart is normally built on.
    var selected by remember { mutableStateOf(listOf(0, 3, 7)) }
    var artwork by remember { mutableStateOf(Artwork.Intent) }
    var amplify by remember { mutableFloatStateOf(1f) }

    fun updateInk(slot: Int, block: RisoInk.() -> RisoInk) {
        press = press.copy(
            inks = press.inks.mapIndexed { index, ink -> if (index == slot) ink.block() else ink },
        )
    }

    fun select(paletteIndex: Int) {
        selected = when {
            paletteIndex in selected -> selected - paletteIndex
            // The chart has no room for a fourth, so the oldest pick comes off to make way.
            selected.size >= CHART_INKS -> selected.drop(1) + paletteIndex
            else -> selected + paletteIndex
            // There has to be something left to chart.
        }.ifEmpty { selected }
    }

    // The press the sliders are configuring is the press everything below prints on, so the picker
    // and the controls sit on the same sheet as the artwork and move with it.
    RisoTheme {
        CompositionLocalProvider(
            LocalPress provides press,
        ) {
            Column(modifier.fillMaxSize().risoPaper(paper).safeDrawingPadding()) {
                val chartInks = selected.mapNotNull { press.inks.getOrNull(it) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (artwork == Artwork.Mixer) 2f else 4 / 3f)
                ) {
                    when (artwork) {
                        Artwork.Mixer -> ColorMixerChart(paper, chartInks)
                        Artwork.Type -> TypeArtwork(press, chartInks)
                        Artwork.Intent -> IntentArtwork(paper, chartInks, amplify)
                        Artwork.Nested -> NestedArtwork(amplify)
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Inks — the whole rack is loaded; pick up to $CHART_INKS to chart")
                    InkPicker(palette, selected, ::select)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        ButtonGroup(
                            selected = artwork,
                            *Artwork.entries.toTypedArray(),
                            modifier = Modifier.padding(vertical = 4.dp),
                            onSelect = { artwork = it },
                        )
                        if (artwork == Artwork.Intent || artwork == Artwork.Nested) {
                            ParamSlider("Amplify registration", amplify, 0f, 8f) { amplify = it }
                        }
                    }

                    item {
                        SectionTitle("Registration")
                        press.inks.forEachIndexed { slot, ink ->
                            ParamSlider("Ink ${slot + 1} offset X", ink.offsetX, -6f, 6f) {
                                updateInk(slot) { copy(offsetX = it) }
                            }
                            ParamSlider("Ink ${slot + 1} offset Y", ink.offsetY, -6f, 6f) {
                                updateInk(slot) { copy(offsetY = it) }
                            }
                        }
                    }

                    item {
                        SectionTitle("Ink")
                        ParamSlider("Spread", press.spread, 0f, 1f) {
                            press = press.copy(spread = it)
                        }
                        ParamSlider("Mottle", press.mottle, 0f, 1f) {
                            press = press.copy(mottle = it)
                        }
                        ParamSlider("Mottle size", press.mottleSize, 1f, 40f) {
                            press = press.copy(mottleSize = it)
                        }
                        ParamSlider("Grain", press.grain, 0f, 1f) { press = press.copy(grain = it) }
                        ParamSlider("Grain size", press.grainSize, 0.5f, 4f) {
                            press = press.copy(grainSize = it)
                        }
                        ParamSlider("Tolerance", press.tolerance, 0f, 0.2f) {
                            press = press.copy(tolerance = it)
                        }
                    }

                    item {
                        SectionTitle("Screen")
                        ParamSlider("Screening", press.screen, 0f, 1f) {
                            press = press.copy(screen = it)
                        }
                        ParamSlider("Dot size", press.dotSize, 1f, 12f) {
                            press = press.copy(dotSize = it)
                        }
                        press.inks.forEachIndexed { slot, ink ->
                            ParamSlider("Ink ${slot + 1} angle", ink.screenAngle, 0f, 90f) {
                                updateInk(slot) { copy(screenAngle = it) }
                            }
                        }
                        ParamSlider("Ink seed", press.seed, 0f, 20f) {
                            press = press.copy(seed = it)
                        }
                    }

                    item {
                        SectionTitle("Paper")
                        paperPresets.forEach { preset ->
                            LabeledSwitch(
                                label = preset.label,
                                checked = paper.colorFront == preset.front &&
                                        paper.colorBack == preset.back,
                                onCheckedChange = {
                                    paper =
                                        paper.copy(
                                            colorFront = preset.front,
                                            colorBack = preset.back
                                        )
                                },
                            )
                        }
                        // Every distinct stock bakes a new surface, synchronously, so these commit on
                        // release rather than baking a texture per frame of a drag.
                        ParamSlider("Contrast", paper.contrast, 0f, 1f, onRelease = true) {
                            paper = paper.copy(contrast = it)
                        }
                        ParamSlider("Roughness", paper.roughness, 0f, 1f, onRelease = true) {
                            paper = paper.copy(roughness = it)
                        }
                        ParamSlider("Fiber", paper.fiber, 0f, 1f, onRelease = true) {
                            paper = paper.copy(fiber = it)
                        }
                        ParamSlider("Fiber size", paper.fiberSize, 0.01f, 1f, onRelease = true) {
                            paper = paper.copy(fiberSize = it)
                        }
                        ParamSlider("Fade", paper.fade, 0f, 1f, onRelease = true) {
                            paper = paper.copy(fade = it)
                        }
                        ParamSlider("Scale", paper.scale, 0.1f, 2f, onRelease = true) {
                            paper = paper.copy(scale = it)
                        }
                        ParamSlider("Paper seed", paper.seed, 0f, 20f, onRelease = true) {
                            paper = paper.copy(seed = it)
                        }
                    }
                }
            }
        }
    }
}

private class PaperPreset(val label: String, val front: Color, val back: Color)

private val paperPresets = listOf(
    PaperPreset("Riso stock", RisoColors.paper, Color.White),
    PaperPreset("Slate on white", Color(0xFF9FADBC), Color.White),
    PaperPreset("Kraft cardboard", Color(0xFF7A5C3E), Color(0xFFC9A06A)),
    PaperPreset("Charcoal on grey", Color(0xFF333333), Color(0xFFBFBFBF)),
    PaperPreset("Blueprint", Color(0xFFE8F0FF), Color(0xFF1B3A6B)),
)

@Composable
private fun InkPicker(
    palette: List<NamedInk>,
    selected: List<Int>,
    onSelect: (Int) -> Unit,
) {
    palette.chunked(6).forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEachIndexed { columnIndex, ink ->
                val index = rowIndex * 6 + columnIndex
                val charted = index in selected
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        // A swatch has to show the ink you are about to pick, not the press's read
                        // of it: the fluorescents sit outside what a density separation can
                        // round-trip, so printed, pink comes back indistinguishable from burgundy.
                        .risoBypass()
                        .background(ink.color)
                        .then(
                            if (charted) {
                                Modifier.border(3.dp, RisoTheme.colors.content)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    // Every ink is a drum, so the number is the same one the sliders below name.
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = RisoTheme.colors.content,
                    )
                }
            }
        }
    }
    Text(
        text = "Charting " + selected.joinToString("   ") { "${it + 1}. ${palette[it].name}" },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * The standard riso color mixer: a Venn of the charted [inks] at full coverage, beside a grid of
 * every tint combination — ink 1 falling across the columns, ink 2 falling and ink 3 rising down the
 * rows.
 *
 * Every patch is authored with [risoOverprint], the exact inverse of the separation, so a cell
 * labelled 60/40 really does come back off the press as the color 60% of one drum and 40% of the
 * other makes. Eyeballing a blend instead would print something else entirely. The chart names its
 * three drums, so those are the drums it prints on.
 */
@Composable
private fun ColorMixerChart(paper: RisoPaper, inks: List<RisoInk>) {
    val gridInks = inks.map { it.color }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp)

    // No background: the press paints its own sheet, and drawing the stock color on top of it would
    // only be printed back as bare paper.
    Canvas(Modifier.fillMaxSize().risoInk(gridInks)) {
        val margin = 12.dp.toPx()
        // Room for the percentage scales along the grid's top, left and right.
        val gutter = 20.dp.toPx()
        val gridSide = min(
            size.height - 2 * margin - gutter,
            (size.width - 3 * margin) / 2f - gutter,
        )
        val gridLeft = size.width - margin - gutter - gridSide
        val gridTop = (size.height - gridSide) / 2f
        val cell = gridSide / GRID_STEPS

        // --- Venn: one circle per ink, multiplied together so the overlaps overprint ---
        val vennWidth = gridLeft - gutter - margin
        val vennCenter = Offset(margin + vennWidth / 2f, size.height / 2f)
        // Circles sit `spread` radii off centre, so the cluster spans 2 * (1 + spread) radii.
        val spread = vennSpread(inks.size)
        val radius = min(vennWidth, size.height - 2 * margin) / (2f * (1f + spread))
        vennCenters(inks.size, vennCenter, radius * spread).forEachIndexed { index, center ->
            drawCircle(
                color = inks[index].color.onRisoPaper(),
                radius = radius,
                center = center,
                // The first pass lands on bare paper; every later one prints over what is there.
                blendMode = if (index == 0) BlendMode.SrcOver else BlendMode.Multiply,
            )
        }

        // --- Grid: every tint combination of the selected inks ---
        for (column in 0 until GRID_STEPS) {
            for (row in 0 until GRID_STEPS) {
                val coverages = listOf(
                    (GRID_STEPS - column) / GRID_STEPS.toFloat(),
                    (GRID_STEPS - row) / GRID_STEPS.toFloat(),
                    (row + 1) / GRID_STEPS.toFloat(),
                )
                drawRect(
                    color = risoOverprint(
                        paper.colorFront,
                        *gridInks.zip(coverages).toTypedArray()
                    ),
                    topLeft = Offset(gridLeft + column * cell, gridTop + row * cell),
                    // Overdraw by a hair: exact edges leave paper-colored seams between cells.
                    size = Size(cell + 1f, cell + 1f),
                )
            }
        }

        // --- Percentage scales, each in its own ink, as on the reference chart ---
        fun label(text: String, color: Color, x: Float, y: Float) {
            val laid = measurer.measure(text, labelStyle.copy(color = color))
            drawText(laid, topLeft = Offset(x - laid.size.width / 2f, y - laid.size.height / 2f))
        }

        for (step in 0 until GRID_STEPS) {
            val percent = "${(GRID_STEPS - step) * 10}%"
            val rising = "${(step + 1) * 10}%"
            inks.getOrNull(0)?.let {
                label(percent, it.color, gridLeft + (step + 0.5f) * cell, gridTop - gutter / 2f)
            }
            inks.getOrNull(1)?.let {
                label(percent, it.color, gridLeft - gutter / 2f, gridTop + (step + 0.5f) * cell)
            }
            inks.getOrNull(2)?.let {
                label(
                    rising,
                    it.color,
                    gridLeft + gridSide + gutter / 2f,
                    gridTop + (step + 0.5f) * cell,
                )
            }
        }
    }
}

/**
 * How far each Venn circle sits from the cluster's centre, in radii.
 *
 * Three circles or more sit on a ring, sized so that neighbours always overlap by the same amount
 * however many there are: the ring grows as drums are added instead of the circles piling up.
 */
private fun vennSpread(count: Int): Float = when {
    count <= 1 -> 0f
    count == 2 -> VENN_SPREAD
    else -> VENN_SPREAD / sin(PI.toFloat() / count)
}

/** Circle centres for a [count]-way Venn, [spread] out from [center] and starting at the top. */
private fun vennCenters(count: Int, center: Offset, spread: Float): List<Offset> = when (count) {
    1 -> listOf(center)
    2 -> listOf(
        center.copy(x = center.x - spread),
        center.copy(x = center.x + spread),
    )

    else -> List(count) { index ->
        val angle = -PI.toFloat() / 2f + 2f * PI.toFloat() * index / count
        Offset(center.x + spread * cos(angle), center.y + spread * sin(angle))
    }
}

/**
 * The same full-spectrum swatch printed and bypassed, side by side. The printed one picks up the
 * grain, the screen and the registration of its passes; the bypassed one comes through the press
 * untouched, as a tipped-in photograph would.
 */
@Composable
private fun BypassComparison(drums: List<Color>) {
    val spectrum = Brush.horizontalGradient(RisoColors.inks.all.map { it.color })

    @Composable
    fun Swatch(label: String, modifier: Modifier) {
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier.fillMaxWidth().height(48.dp).background(spectrum, RoundedCornerShape(8.dp))
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        // The printed one is separated onto the drums it names, so a spectrum spanning the whole
        // rack is squeezed into three inks and picks up their screen and grain; the bypassed one
        // comes through untouched.
        Swatch("printed", Modifier.risoInk(drums))
        Swatch("risoBypass", Modifier.risoBypass(cornerRadius = 8.dp))
    }
}

/** Which artwork the press is running. */
private enum class Artwork(override val text: String) : ButtonGroupItem {
    Type("Type"),
    Mixer("Mixer"),
    Intent("Intent"),
    Nested("Nested"),
}

/**
 * Coverages the intent disc is authored at, per drum. Deliberately uneven: a color mixed from three
 * drums at different strengths is exactly the kind a separation could not read back unambiguously,
 * which is what naming the drums is for.
 */
private val INTENT_COVERAGES = listOf(0.85f, 0.55f, 0.35f)

/**
 * A disc printed on the drums it names, with its title knocked out of the ink.
 *
 * The disc's color is authored with [risoOverprint] from the charted inks, and those same inks are
 * named with [risoInk], so it prints on the drums it was mixed from and at the coverages it was
 * authored with. Wind [amplify] up and each drum throws its pass further off register, until the
 * recipe is legible directly off the artwork as one fringe per ink.
 *
 * The title is set in the stock's own color, which is a knockout: a press cannot print white, so
 * paper-colored artwork comes off as a hole in the ink rather than as a second color laid over it.
 *
 * Note what this artwork no longer needs. The knockout is simply drawn into the disc, and the whole
 * pass — disc, hole and all — is translated as one, so the hole travels with the ink around it and
 * stays a clean hole however far the pass is thrown. When each drum instead re-read the finished page
 * at an offset, every pass filled the hole in from its own direction and the type turned to mud; the
 * title had to be given a region of its own, printed in perfect register, with a standoff wide enough
 * to cover the distance a pass could reach. All of that was the price of separating after the fact.
 */
@Composable
private fun IntentArtwork(paper: RisoPaper, inks: List<RisoInk>, amplify: Float) {
    val drums = inks.map { it.color }
    val recipe = drums.zip(INTENT_COVERAGES).toTypedArray()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .risoInk(drums, offsetScale = amplify)
                .background(
                    color = risoOverprint(paper.colorFront, *recipe),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "RISO",
                style = MaterialTheme.typography.displaySmall,
                // The stock's own color: a hole in the ink, not a color printed over it. Drawn
                // straight into the disc's artwork, so it is part of the same pass.
                color = paper.colorFront,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A component with its own inks inside a panel with different ones — the case that decides what
 * nesting means.
 *
 * The innermost wins. The panel records its content and leaves a hole where the button goes, then
 * lays the button down itself, at the button's place on the page, once its own drum is on the sheet.
 * So the button keeps its pink and purple wherever it is dropped, and neither of them is inked twice.
 * Wind [amplify] up and the two nest visibly: the panel's black throws one way, the button's pair
 * throws its own.
 */
@Composable
private fun NestedArtwork(amplify: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .risoInk(RisoTheme.colors.content, offsetScale = amplify)
                .background(RisoTheme.colors.content.onRisoPaper(0.3f), RoundedCornerShape(12.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(text = "Nested", onClick = {})
        }
    }
}

/** Type set in each ink, where misregistration is most visible. */
@Composable
private fun TypeArtwork(press: Press, inks: List<RisoInk>) {
    val drums = inks.map { it.color }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RISO",
            style = MaterialTheme.typography.displayLarge,
            color = inks.last().color,
            modifier = Modifier.fillMaxWidth().risoInk(inks.last().color),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${press.inks.size} drums, one pass each",
            style = MaterialTheme.typography.titleMedium,
            color = inks.first().color,
            modifier = Modifier.fillMaxWidth().risoInk(inks.first().color),
            textAlign = TextAlign.Center,
        )
        // On the black drum, and only that one. Type is where the choice bites hardest: a pass
        // carries the whole glyph, so a caption separated across three drums comes off the press as
        // three displaced captions — which is what a badly registered print really does, and why
        // body copy goes on one drum.
        Text(
            text = "each pass carries the whole glyph",
            style = MaterialTheme.typography.bodyMedium,
            color = RisoTheme.colors.content,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                .risoInk(RisoTheme.colors.content),
            textAlign = TextAlign.Center,
        )
        BypassComparison(drums)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * A labelled slider. Set [onRelease] for a value that is expensive to change — the handle then tracks
 * the drag locally and reports once, on release, instead of on every frame of it.
 */
@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int = 0,
    onRelease: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    // Reset whenever the value changes underneath us, so a preset or a reset still moves the handle.
    var dragged by remember(value) { mutableStateOf(value) }
    val shown = if (onRelease) dragged else value

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Body("$label: ${(shown * 100).roundToInt() / 100f}")
        Slider(
            value = shown,
            onValueChange = { if (onRelease) dragged = it else onValueChange(it) },
            onValueChangeFinished = { if (onRelease) onValueChange(dragged) },
            valueRange = min..max,
            steps = steps,
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
