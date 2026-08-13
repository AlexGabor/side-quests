package com.alexgabor.design.riso.print

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
import com.alexgabor.design.riso.attributes.NamedInk
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.bypass.risoBypass
import com.alexgabor.design.riso.components.ButtonGroup
import com.alexgabor.design.riso.components.ButtonGroupItem
import com.alexgabor.design.riso.separation.risoInk
import kotlin.math.PI
import kotlin.math.abs
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
 * Playground for [risoPrint]: load any of the RISO inks onto the press and see how they mix, over a
 * full set of controls for the press itself.
 */
@Preview
@Composable
fun RisoPrintDemo(modifier: Modifier = Modifier) {
    val palette = RisoColors.inks.all
    // The whole rack is loaded, always — a colour is separated onto the few drums that can print it,
    // so leaving them all mounted costs next to nothing. Picking a swatch chooses what the chart
    // ramps against, not what the press is carrying.
    var params by remember { mutableStateOf(RisoPrintParams()) }
    // Pink, yellow and blue: the subtractive triad a printed mixer chart is normally built on.
    var selected by remember { mutableStateOf(listOf(0, 3, 7)) }
    var artwork by remember { mutableStateOf(Artwork.Intent) }
    var amplify by remember { mutableFloatStateOf(1f) }

    fun updateInk(slot: Int, block: RisoInk.() -> RisoInk) {
        params = params.copy(
            inks = params.inks.mapIndexed { index, ink -> if (index == slot) ink.block() else ink },
        )
    }

    fun updatePaper(block: RisoPaper.() -> RisoPaper) {
        params = params.copy(paper = params.paper.block())
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

    // One press for the whole screen, so the stock is the same sheet everywhere — the artwork, the
    // picker and the controls all sit on the paper being configured, and every one of them moves
    // when it changes.
    Column(modifier.fillMaxSize().risoPrint(params).safeDrawingPadding()) {
        val chartInks = selected.mapNotNull { params.inks.getOrNull(it) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (artwork == Artwork.Mixer) 2f else 4 / 3f)
        ) {
            when (artwork) {
                Artwork.Mixer -> ColorMixerChart(params, chartInks)
                Artwork.Type -> TypeArtwork(params, chartInks)
                Artwork.Intent -> IntentArtwork(params, chartInks, amplify)
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
                    // The two inks this component is drawn in, so the press prints its rule in the
                    // one and its selected pill in the other, rather than separating each onto
                    // whichever drums happen to make the colour. Named as the inks themselves, not
                    // as they come off the paper.
                    modifier = Modifier.padding(vertical = 4.dp)
                        .risoInk(RisoTheme.colors.content, RisoTheme.colors.accent),
                    onSelect = { artwork = it },
                )
                if (artwork == Artwork.Intent) {
                    ParamSlider("Amplify registration", amplify, 0f, 12f) { amplify = it }
                }
            }

            item {
                SectionTitle("Registration")
                params.inks.forEachIndexed { slot, ink ->
                    ParamSlider("Ink ${slot + 1} offset X", ink.offsetX, -6f, 6f) {
                        updateInk(slot) { copy(offsetX = it) }
                    }
                    ParamSlider("Ink ${slot + 1} offset Y", ink.offsetY, -6f, 6f) {
                        updateInk(slot) { copy(offsetY = it) }
                    }
                }
                ParamSlider("Wobble", params.wobble, 0f, 4f) { params = params.copy(wobble = it) }
            }

            item {
                SectionTitle("Ink")
                ParamSlider("Overprint (stacked -> juxtaposed)", params.overprint, 0f, 1f) {
                    params = params.copy(overprint = it)
                }
                ParamSlider("Spread", params.spread, 0f, 1f) { params = params.copy(spread = it) }
                ParamSlider("Mottle", params.mottle, 0f, 1f) { params = params.copy(mottle = it) }
                ParamSlider("Mottle size", params.mottleSize, 1f, 40f) {
                    params = params.copy(mottleSize = it)
                }
                ParamSlider("Grain", params.grain, 0f, 1f) { params = params.copy(grain = it) }
                ParamSlider("Grain size", params.grainSize, 0.5f, 4f) {
                    params = params.copy(grainSize = it)
                }
            }

            item {
                SectionTitle("Screen")
                ParamSlider("Screening", params.screen, 0f, 1f) {
                    params = params.copy(screen = it)
                }
                ParamSlider("Dot size", params.dotSize, 1f, 12f) {
                    params = params.copy(dotSize = it)
                }
                params.inks.forEachIndexed { slot, ink ->
                    ParamSlider("Ink ${slot + 1} angle", ink.screenAngle, 0f, 90f) {
                        updateInk(slot) { copy(screenAngle = it) }
                    }
                }
                ParamSlider("Ink seed", params.seed, 0f, 20f) { params = params.copy(seed = it) }
            }

            item {
                SectionTitle("Paper")
                paperPresets.forEach { preset ->
                    LabeledSwitch(
                        label = preset.label,
                        checked = params.paper.colorFront == preset.front &&
                            params.paper.colorBack == preset.back,
                        onCheckedChange = {
                            updatePaper { copy(colorFront = preset.front, colorBack = preset.back) }
                        },
                    )
                }
                // Every distinct stock bakes a new surface, synchronously, so the surface sliders
                // commit on release rather than baking a texture per frame of a drag. Tolerance is
                // not one of them — it only moves a uniform.
                ParamSlider("Contrast", params.paper.contrast, 0f, 1f, onRelease = true) {
                    updatePaper { copy(contrast = it) }
                }
                ParamSlider("Roughness", params.paper.roughness, 0f, 1f, onRelease = true) {
                    updatePaper { copy(roughness = it) }
                }
                ParamSlider("Fiber", params.paper.fiber, 0f, 1f, onRelease = true) {
                    updatePaper { copy(fiber = it) }
                }
                ParamSlider("Fiber size", params.paper.fiberSize, 0.01f, 1f, onRelease = true) {
                    updatePaper { copy(fiberSize = it) }
                }
                ParamSlider("Fade", params.paper.fade, 0f, 1f, onRelease = true) {
                    updatePaper { copy(fade = it) }
                }
                ParamSlider("Scale", params.paper.scale, 0.1f, 2f, onRelease = true) {
                    updatePaper { copy(scale = it) }
                }
                ParamSlider("Paper seed", params.paper.seed, 0f, 20f, onRelease = true) {
                    updatePaper { copy(seed = it) }
                }
                ParamSlider("Tolerance", params.paper.tolerance, 0f, 0.2f) {
                    updatePaper { copy(tolerance = it) }
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
                        // Tipping the whole rack in costs every pixel a dozen bounds tests, which
                        // is a price only a playground should pay.
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
 * The standard riso colour mixer: a Venn of the charted [inks] at full coverage, beside a grid of
 * every tint combination — ink 1 falling across the columns, ink 2 falling and ink 3 rising down
 * the rows. The press carries its whole rack; a chart has only these axes, which is why no more
 * than [CHART_INKS] can be picked.
 *
 * Every patch is authored with [risoOverprint], which is the exact inverse of the shader's
 * separation, so a cell labelled 60/40 really does come back off the press as the colour 60% of one
 * drum and 40% of the other makes. Eyeballing a blend instead would print something else entirely.
 * With the full rack loaded the press may reach for other drums to make that colour — see
 * [risoOverprint] — which is visible here as a patch printing in a screen angle you did not pick.
 */
@Composable
private fun ColorMixerChart(params: RisoPrintParams, inks: List<RisoInk>) {
    val gridInks = inks.map { it.color }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp)

    // No background: the press paints its own sheet, and drawing the stock colour on top of it
    // would only be printed back as bare paper.
    Canvas(Modifier.fillMaxSize()) {
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
                    color = risoOverprint(params.paper.colorFront, *gridInks.zip(coverages).toTypedArray()),
                    topLeft = Offset(gridLeft + column * cell, gridTop + row * cell),
                    // Overdraw by a hair: exact edges leave paper-coloured seams between cells.
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
 * The same full-spectrum swatch printed and bypassed, side by side. The printed one is projected
 * onto whichever inks are loaded and picks up the grain, screen and registration error of the pass;
 * the bypassed one comes through the press untouched, as a tipped-in photograph would.
 */
@Composable
private fun BypassComparison() {
    val spectrum = Brush.horizontalGradient(RisoColors.inks.all.map { it.color })

    @Composable
    fun Swatch(label: String, modifier: Modifier) {
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier.fillMaxWidth().height(48.dp).background(spectrum, RoundedCornerShape(8.dp)))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        Swatch("printed", Modifier)
        Swatch("risoBypass", Modifier.risoBypass(cornerRadius = 8.dp))
    }
}

/** Which artwork the press is running. */
private enum class Artwork(override val text: String) : ButtonGroupItem {
    Type("Type"),
    Mixer("Mixer"),
    Intent("Intent"),
}

/**
 * Coverages the intent disc is authored at, per drum. Deliberately uneven: a colour mixed from three
 * drums at different strengths is exactly the kind a separation cannot read back unambiguously,
 * which is what [risoInk] is for.
 */
private val INTENT_COVERAGES = listOf(0.85f, 0.55f, 0.35f)

/**
 * A disc printed on the drums it names, with its title knocked out of the ink — the artwork for
 * reading [risoInk] off the press.
 *
 * The disc's colour is authored with [risoOverprint] from the charted inks, and those same inks are
 * named with [risoInk], so the press prints it on the drums it was mixed from instead of whatever
 * the separation would otherwise reach for — and at the coverages it was authored with, since the
 * restricted separation inverts exactly the mix that made the colour. Wind [amplify] up and each of
 * those drums throws its pass further off register, until the recipe is legible directly off the
 * artwork as one fringe per ink.
 *
 * The title is set in the stock's own colour, which is a knockout: a press cannot print white, so
 * paper-coloured artwork comes off as a hole in the ink rather than as a second colour laid over it.
 * A hole is where amplified registration hurts most — each drum samples the artwork off where the
 * glyph actually is, so every pass fills the hole in from its own direction and the type turns to
 * mud. The title's own region prints in perfect register, so the hole is punched exactly where it
 * was drawn and the type stays readable however far the disc around it is thrown.
 *
 * That region is the title's rectangle, so the disc stops fringing inside it too — a sharp block of
 * registered ink in the middle of a smeared one. That is what a region is: bounds, not a shape.
 */
@Composable
private fun IntentArtwork(params: RisoPrintParams, inks: List<RisoInk>, amplify: Float) {
    val drums = inks.map { it.color }
    val recipe = inks.map { it.color }.zip(INTENT_COVERAGES).toTypedArray()
    val diameter = 240.dp

    // How far the disc's amplified passes reach. A pixel *outside* the title's region still samples
    // the artwork from this far away, so it can read a glyph from outside and punch a second,
    // displaced hole through the type. Registering the type is not enough on its own: its region has
    // to own the whole band the type can be reached from.
    val reach = params.inks.maxOfOrNull { maxOf(abs(it.offsetX), abs(it.offsetY)) } ?: 0f
    val standoff = (reach * amplify + params.wobble).dp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(diameter)
                // The region is a rounded rectangle, so a full corner radius makes it the disc.
                .risoInk(drums, offsetScale = amplify, cornerRadius = diameter / 2)
                .background(
                    color = risoOverprint(params.paper.colorFront, *recipe),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "RISO",
                style = MaterialTheme.typography.displaySmall,
                // The stock's own colour: a hole in the ink, not a colour printed over it.
                color = params.paper.colorFront,
                textAlign = TextAlign.Center,
                // The same drums as the disc — a region names the whole rack it prints with, so
                // naming none here would knock the title's box out to bare paper instead of leaving
                // the disc's ink around the glyphs. The padding sits inside the region, so the
                // region is the type plus its standoff.
                modifier = Modifier
                    .risoInk(drums, offsetScale = 0f)
                    .padding(standoff),
            )
        }
    }
}

/** Type set in each ink, where misregistration is most visible. */
@Composable
private fun TypeArtwork(params: RisoPrintParams, inks: List<RisoInk>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RISO",
            style = MaterialTheme.typography.displayLarge,
            color = inks.last().color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${params.inks.size} drums, one pass each",
            style = MaterialTheme.typography.titleMedium,
            color = inks.first().color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "where the passes overlap, a new colour is made",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        BypassComparison()
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
 * A labelled slider. Set [onRelease] for a value that is expensive to change — the handle then
 * tracks the drag locally and reports once, on release, instead of on every frame of it.
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
