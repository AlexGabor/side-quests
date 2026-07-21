package com.alexgabor.design.riso.paper


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import kotlin.math.roundToInt

private data class ColorPreset(val label: String, val front: Color, val back: Color)

private val colorPresets = listOf(
    ColorPreset("Slate on white", Color(0xFF9FADBC), Color.White),
    ColorPreset("Kraft cardboard", Color(0xFF7A5C3E), Color(0xFFC9A06A)),
    ColorPreset("Charcoal on grey", Color(0xFF333333), Color(0xFFBFBFBF)),
    ColorPreset("Blueprint", Color(0xFFE8F0FF), Color(0xFF1B3A6B)),
)

@Composable
private fun PaperTextureDemoScreen(
    modifier: Modifier = Modifier,
) {
    var params by remember { mutableStateOf(PaperTextureParams()) }
    var useImage by remember { mutableStateOf(false) }
    var presetIndex by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize().paperTexture(params)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    Spacer(
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(16/9f)
                            .background(RisoTheme.colors.content),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Paper Texture",
                            style = MaterialTheme.typography.headlineMedium,
                            color = params.colorFront,
                        )
                    }
                }
            }

            item {
                LabeledSwitch(
                    label = "Apply over image content",
                    checked = useImage,
                    onCheckedChange = { useImage = it },
                )
            }

            item {
                Text(
                    text = "Color preset",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                colorPresets.forEachIndexed { index, preset ->
                    LabeledSwitch(
                        label = preset.label,
                        checked = presetIndex == index,
                        onCheckedChange = {
                            presetIndex = index
                            params = params.copy(
                                colorFront = preset.front,
                                colorBack = preset.back,
                            )
                        },
                    )
                }
            }

            item {
                ParamSlider("Contrast", params.contrast, 0f, 1f) {
                    params = params.copy(contrast = it)
                }
            }
            item {
                ParamSlider("Roughness", params.roughness, 0f, 1f) {
                    params = params.copy(roughness = it)
                }
            }
            item {
                ParamSlider("Fiber", params.fiber, 0f, 1f) { params = params.copy(fiber = it) }
            }
            item {
                ParamSlider("Fiber size", params.fiberSize, 0.01f, 1f) {
                    params = params.copy(fiberSize = it)
                }
            }
            item {
                ParamSlider("Crumples", params.crumples, 0f, 1f) {
                    params = params.copy(crumples = it)
                }
            }
            item {
                ParamSlider("Crumple size", params.crumpleSize, 0.01f, 1f) {
                    params = params.copy(crumpleSize = it)
                }
            }
            item {
                ParamSlider("Folds", params.folds, 0f, 1f) { params = params.copy(folds = it) }
            }
            item {
                ParamSlider("Fold count", params.foldCount, 0f, 15f, steps = 14) {
                    params = params.copy(foldCount = it.roundToInt().toFloat())
                }
            }
            item {
                ParamSlider("Drops", params.drops, 0f, 1f) { params = params.copy(drops = it) }
            }
            item {
                ParamSlider("Fade", params.fade, 0f, 1f) { params = params.copy(fade = it) }
            }
            item {
                ParamSlider("Scale", params.scale, 0.1f, 2f) { params = params.copy(scale = it) }
            }
            item {
                ParamSlider("Seed", params.seed, 0f, 20f) { params = params.copy(seed = it) }
            }
        }
    }
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
