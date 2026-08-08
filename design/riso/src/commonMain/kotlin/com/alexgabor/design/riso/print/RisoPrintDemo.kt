package com.alexgabor.design.riso.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.NamedInk
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.bypass.risoBypass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Keeps a drum's registration when only its colour changes; defaults it when one is added. */
private fun inkForSlot(slot: Int, color: Color, existing: RisoInk?): RisoInk =
    existing?.copy(color = color) ?: risoInkForSlot(slot, color)

/** Tint steps down each axis of the mixer grid, as on a press's tint scale. */
private const val GRID_STEPS = 10

/** How far two neighbouring Venn circles sit apart, in radii. */
private const val VENN_SPREAD = 0.62f

/** The first three drums are the ones the mixer grid ramps against; the rest only show in the Venn. */
private const val GRID_INKS = 3

/**
 * Playground for [risoPrint]: load any of the RISO inks onto the press and see how they mix, over a
 * full set of controls for the press itself.
 */
@Composable
private fun RisoPrintDemo(modifier: Modifier = Modifier) {
    val palette = RisoColors.inks.all
    var selected by remember { mutableStateOf(palette.indices.toList()) }
    var params by remember {
        mutableStateOf(
            RisoPrintParams(
                inks = selected.mapIndexed { slot, index ->
                    inkForSlot(slot, palette[index].color, null)
                },
            ),
        )
    }
    var showMixer by remember { mutableStateOf(true) }

    fun updateInk(slot: Int, block: RisoInk.() -> RisoInk) {
        params = params.copy(
            inks = params.inks.mapIndexed { index, ink -> if (index == slot) ink.block() else ink },
        )
    }

    fun updatePaper(block: RisoPaper.() -> RisoPaper) {
        params = params.copy(paper = params.paper.block())
    }

    fun select(paletteIndex: Int) {
        val next = if (paletteIndex in selected) selected - paletteIndex else selected + paletteIndex
        // At least one drum has to be loaded for there to be a print at all.
        if (next.isEmpty()) return
        params = params.copy(
            inks = next.mapIndexed { slot, index ->
                inkForSlot(slot, palette[index].color, params.inks.getOrNull(slot))
            },
        )
        selected = next
    }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (showMixer) 2f else 4 / 3f)
                .risoPrint(params)
        ) {
            if (showMixer) ColorMixerChart(params) else TypeArtwork(params)
        }

        // Deliberately outside the print: a swatch has to show the ink you are about to load, and
        // a press that is not carrying yellow renders the yellow swatch as whatever it can hit.
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("Inks — one drum, one pass each")
            InkPicker(palette, selected, ::select)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().risoPrint(params),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                LabeledSwitch(
                    label = "Colour mixer chart",
                    checked = showMixer,
                    onCheckedChange = { showMixer = it },
                )
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
                val slot = selected.indexOf(index)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(ink.color)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    // The drum number, so the registration sliders below are unambiguous.
                    if (slot >= 0) {
                        Text(
                            text = "${slot + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = RisoTheme.colors.content,
                        )
                    }
                }
            }
        }
    }
    Text(
        text = selected.mapIndexed { slot, index -> "${slot + 1}. ${palette[index].name}" }
            .joinToString("   "),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * The standard riso colour mixer: a Venn of the loaded inks at full coverage, beside a grid of
 * every tint combination — ink 1 falling across the columns, ink 2 falling and ink 3 rising down
 * the rows. A press can carry more drums than that, but a chart only has two axes, so the grid
 * ramps the first [GRID_INKS] and leaves the rest to the Venn.
 *
 * Every patch is authored with [risoOverprint], which is the exact inverse of the shader's
 * separation, so a cell labelled 60/40 really does come back off the press as the colour 60% of one
 * drum and 40% of the other makes. Eyeballing a blend instead would print something else entirely.
 * With a long rack loaded the press may reach for other drums to make that colour — see
 * [risoOverprint] — which is visible here as a patch printing in a screen angle you did not pick.
 */
@Composable
private fun ColorMixerChart(params: RisoPrintParams) {
    val inks = params.inks
    val gridInks = inks.take(GRID_INKS).map { it.color }
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
                color = inks[index].color,
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
                    color = risoOverprint(params.paper.colorFront, gridInks, coverages),
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

/** Type set in each ink, where misregistration is most visible. */
@Composable
private fun TypeArtwork(params: RisoPrintParams) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RISO",
            style = MaterialTheme.typography.displayLarge,
            color = params.inks.last().color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${params.inks.size} drums, one pass each",
            style = MaterialTheme.typography.titleMedium,
            color = params.inks.first().color,
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
        Text(
            text = "$label: ${(shown * 100).roundToInt() / 100f}",
            style = MaterialTheme.typography.bodyMedium,
        )
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
