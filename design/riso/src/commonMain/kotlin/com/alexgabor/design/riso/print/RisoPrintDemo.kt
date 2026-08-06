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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexgabor.design.riso.attributes.NamedInk
import com.alexgabor.design.riso.attributes.RisoColors
import com.alexgabor.design.riso.paper.paperTexture
import kotlin.math.min
import kotlin.math.roundToInt

private data class Registration(val offsetX: Float, val offsetY: Float, val screenAngle: Float)

/** Registration error and screen angle each drum starts with, by printing order. */
private val defaultRegistration = listOf(
    Registration(-1.2f, 0.8f, 15f),
    Registration(0.9f, -0.5f, 75f),
    Registration(0.4f, 1.1f, 45f),
)

/** Keeps a drum's registration when only its colour changes; defaults it when one is added. */
private fun inkForSlot(slot: Int, color: Color, existing: RisoInk?): RisoInk {
    val default = defaultRegistration[slot]
    return existing?.copy(color = color)
        ?: RisoInk(color, default.offsetX, default.offsetY, default.screenAngle)
}

/** Tint steps down each axis of the mixer grid, as on a press's tint scale. */
private const val GRID_STEPS = 10

/** How far each Venn circle sits from the cluster's centre, in radii. */
private const val VENN_SPREAD = 0.62f

/**
 * Playground for [risoPrint]: pick up to [MAX_INKS] of the RISO inks and see how they mix, over a
 * full set of controls for the press itself.
 */
@Composable
fun RisoPrintDemo(modifier: Modifier = Modifier) {
    val palette = RisoColors.inks.all
    var selected by remember { mutableStateOf(listOf(0, 7)) }
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

    fun select(paletteIndex: Int) {
        val next = when {
            paletteIndex in selected -> selected - paletteIndex
            selected.size < MAX_INKS -> selected + paletteIndex
            else -> return
        }
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
                .paperTexture()
                .risoPrint(params)
        ) {
            if (showMixer) ColorMixerChart(params) else TypeArtwork(params)
        }

        // Deliberately outside the print: a swatch has to show the ink you are about to load, and
        // a press that is not carrying yellow renders the yellow swatch as whatever it can hit.
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("Inks — pick up to $MAX_INKS")
            InkPicker(palette, selected, params.paper, ::select)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().paperTexture().risoPrint(params),
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
                ParamSlider("Seed", params.seed, 0f, 20f) { params = params.copy(seed = it) }
            }
        }
    }
}

@Composable
private fun InkPicker(
    palette: List<NamedInk>,
    selected: List<Int>,
    paper: Color,
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
                            color = paper,
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
 * The standard riso colour mixer: a Venn of the selected inks at full coverage, beside a grid of
 * every tint combination — ink 1 falling across the columns, ink 2 falling and ink 3 rising down
 * the rows.
 *
 * Every patch is authored with [risoOverprint], which is the exact inverse of the shader's
 * separation, so a cell labelled 60/40 really does come back off the press as 60% of one drum and
 * 40% of the other. Eyeballing a blend instead would separate into something else entirely.
 */
@Composable
private fun ColorMixerChart(params: RisoPrintParams) {
    val inks = params.inks.take(MAX_INKS)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 7.sp)

    Canvas(Modifier.fillMaxSize().background(params.paper)) {
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
        // Circles sit VENN_SPREAD radii off centre, so the cluster spans 2 * (1 + spread) radii.
        val radius = min(vennWidth, size.height - 2 * margin) / (2f * (1f + VENN_SPREAD))
        vennCenters(inks.size, vennCenter, radius * VENN_SPREAD).forEachIndexed { index, center ->
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
                    color = risoOverprint(params.paper, inks, coverages),
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

/** Circle centres for a 1-, 2- or 3-way Venn, spread [spread] from [center]. */
private fun vennCenters(count: Int, center: Offset, spread: Float): List<Offset> = when (count) {
    1 -> listOf(center)
    2 -> listOf(
        center.copy(x = center.x - spread),
        center.copy(x = center.x + spread),
    )
    else -> listOf(
        Offset(center.x, center.y - spread),
        Offset(center.x - spread * 0.87f, center.y + spread * 0.5f),
        Offset(center.x + spread * 0.87f, center.y + spread * 0.5f),
    )
}

/** Type set in each ink, where misregistration is most visible. */
@Composable
private fun TypeArtwork(params: RisoPrintParams) {
    Column(
        modifier = Modifier.fillMaxSize().background(params.paper).padding(24.dp),
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

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "$label: ${(value * 100).roundToInt() / 100f}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
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
