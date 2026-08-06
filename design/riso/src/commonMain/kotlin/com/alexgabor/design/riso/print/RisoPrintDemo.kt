package com.alexgabor.design.riso.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class InkPreset(val label: String, val a: Color, val b: Color)

private val inkPresets = listOf(
    InkPreset("Fluo pink + blue", RisoInk.FluorescentPink, RisoInk.Blue),
    InkPreset("Fluo pink + federal blue", RisoInk.FluorescentPink, RisoInk.FederalBlue),
    InkPreset("Yellow + blue", RisoInk.Yellow, RisoInk.Blue),
    InkPreset("Fluo pink + black", RisoInk.FluorescentPink, RisoInk.Black),
)

/**
 * Playground for [risoPrint]: a fixed piece of artwork printed with the current params, over a
 * full set of controls. The controls themselves are deliberately left un-printed so they stay
 * legible while you dial the registration in.
 */
@Composable
fun RisoPrintDemo(modifier: Modifier = Modifier) {
    var params by remember { mutableStateOf(RisoPrintParams()) }
    var presetIndex by remember { mutableIntStateOf(0) }
    var showRegistrationTest by remember { mutableStateOf(true) }

    fun updateInkA(block: RisoInk.() -> RisoInk) {
        params = params.copy(inkA = params.inkA.block())
    }

    fun updateInkB(block: RisoInk.() -> RisoInk) {
        params = params.copy(inkB = params.inkB.block())
    }

    Column(modifier.fillMaxSize().background(params.paper)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4 / 3f)
                .risoPrint(params),
        ) {
            if (showRegistrationTest) {
                RegistrationTestArtwork(params)
            } else {
                TypeArtwork(params)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().risoPrint(params),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                LabeledSwitch(
                    label = "Registration test artwork",
                    checked = showRegistrationTest,
                    onCheckedChange = { showRegistrationTest = it },
                )
            }

            item {
                SectionTitle("Inks")
                inkPresets.forEachIndexed { index, preset ->
                    LabeledSwitch(
                        label = preset.label,
                        checked = presetIndex == index,
                        onCheckedChange = {
                            presetIndex = index
                            params = params.copy(
                                inkA = params.inkA.copy(color = preset.a),
                                inkB = params.inkB.copy(color = preset.b),
                            )
                        },
                    )
                }
            }

            item {
                SectionTitle("Registration")
                ParamSlider("Ink A offset X", params.inkA.offsetX, -6f, 6f) {
                    updateInkA { copy(offsetX = it) }
                }
                ParamSlider("Ink A offset Y", params.inkA.offsetY, -6f, 6f) {
                    updateInkA { copy(offsetY = it) }
                }
                ParamSlider("Ink B offset X", params.inkB.offsetX, -6f, 6f) {
                    updateInkB { copy(offsetX = it) }
                }
                ParamSlider("Ink B offset Y", params.inkB.offsetY, -6f, 6f) {
                    updateInkB { copy(offsetY = it) }
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
                ParamSlider("Ink A angle", params.inkA.screenAngle, 0f, 90f) {
                    updateInkA { copy(screenAngle = it) }
                }
                ParamSlider("Ink B angle", params.inkB.screenAngle, 0f, 90f) {
                    updateInkB { copy(screenAngle = it) }
                }
                ParamSlider("Seed", params.seed, 0f, 20f) { params = params.copy(seed = it) }
            }
        }
    }
}

/**
 * Two overlapping shapes plus swatches: shows the registration error on the edges and the
 * overprint colour where the inks cross.
 *
 * The second shape is drawn with [BlendMode.Multiply] rather than painted over the first. That
 * matters: the modifier separates whatever colour reaches it, so an opaque shape simply knocks the
 * ink underneath it out of the artwork and there is nothing left to overprint.
 */
@Composable
private fun RegistrationTestArtwork(params: RisoPrintParams) {
    Box(Modifier.fillMaxSize().background(params.paper), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = 75.dp.toPx()
            val shift = 30.dp.toPx()
            drawCircle(
                color = params.inkA.color,
                radius = radius,
                center = center.copy(x = center.x - shift),
            )
            drawRoundRect(
                color = params.inkB.color,
                topLeft = Offset(center.x + shift - radius, center.y - radius),
                size = Size(2 * radius, 2 * radius),
                cornerRadius = CornerRadius(24.dp.toPx()),
//                blendMode = BlendMode.Multiply,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(28.dp).background(params.inkA.color))
            Box(Modifier.size(28.dp).background(params.inkB.color))
            Box(Modifier.size(28.dp).background(Color.Black))
            Box(Modifier.size(28.dp).background(Color.Gray))
        }
    }
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
            color = params.inkB.color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "two drums, one pass each",
            style = MaterialTheme.typography.titleMedium,
            color = params.inkA.color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "where the passes overlap, a third colour is made",
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
