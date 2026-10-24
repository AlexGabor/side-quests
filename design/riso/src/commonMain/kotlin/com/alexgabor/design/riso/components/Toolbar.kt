package com.alexgabor.design.riso.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper

enum class ToolbarSize {
    Large, Medium,
}

@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    size: ToolbarSize = ToolbarSize.Large,
    startContent: @Composable (() -> Unit)? = null,
    endContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val lineColor = RisoTheme.colors.content
    val dimens = RisoTheme.dimens
    val (lineWidth, topPadding) = when (size) {
        ToolbarSize.Large -> dimens.lineWidth to dimens.lineWidth + 2.dp
        ToolbarSize.Medium -> dimens.lineWidth / 2 to dimens.lineWidth
    }
    Row(
        modifier
            .drawBehind {
                drawRect(
                    color = lineColor,
                    // `this.size`, as the `size` parameter shadows the draw scope's.
                    size = Size(this.size.width, lineWidth.toPx()),
                )
            }
            .padding(top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startContent?.invoke()
        Box(Modifier.weight(1f)) {
            content()
        }
        endContent?.invoke()
    }
}

@Preview
@Composable
private fun ToolbarPreview() {
    RisoTheme {
        Column(
            Modifier.fillMaxSize()
                .risoPaper()
                .risoInk(RisoTheme.colors.content)
                .safeDrawingPadding()
                .padding(RisoTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Toolbar(
                modifier = Modifier.fillMaxWidth(),
                startContent = {
                    FlatButton(icon = IconType.Back, onClick = {}, contentDescription = "Back")
                },
                endContent = {
                    FlatButton(icon = IconType.Settings, onClick = {}, contentDescription = "Settings")
                },
            ) {
                Heading1(text = "Large")
            }
            Toolbar(
                modifier = Modifier.fillMaxWidth(),
                size = ToolbarSize.Medium,
            ) {
                Heading2(text = "Medium")
            }
        }
    }
}
