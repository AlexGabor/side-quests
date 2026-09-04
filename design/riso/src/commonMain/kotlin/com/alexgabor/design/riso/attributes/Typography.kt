package com.alexgabor.design.riso.attributes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexgabor.design.riso.FiraCode_VF
import com.alexgabor.design.riso.Res
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper
import org.jetbrains.compose.resources.Font

@Stable
@Composable
internal fun firaCodeFamily() = FontFamily(
    Font(
        resource = Res.font.FiraCode_VF
    )
)

@Immutable
class Typography(
    private val fontFamily: FontFamily?,
    val heading1: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 32.sp
    ),
    val heading2: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp
    ),
    val heading3: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 15.sp
    ),
)

val LocalTypography = staticCompositionLocalOf { Typography(fontFamily = null) }

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RisoTheme.colors.content,
    textStyle: TextStyle,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = textStyle,
    )
}

@Composable
fun Heading1(
    text: String,
    modifier: Modifier = Modifier,
    startContent: @Composable (() -> Unit)? = null,
    endContent: @Composable (() -> Unit)? = null,
) {
    val lineColor = RisoTheme.colors.content
    val lineWidth = RisoTheme.dimens.lineWidth
    Row(
        modifier
            .drawBehind {
                drawRect(
                    color = lineColor,
                    size = Size(size.width, lineWidth.toPx()),
                )
            }
            .padding(top = RisoTheme.dimens.lineWidth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        startContent?.invoke()
        Text(
            text = text,
            textStyle = RisoTheme.typography.heading1,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        endContent?.invoke()
    }
}


@Composable
fun Heading2(
    text: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = RisoTheme.colors.content
    val lineWidth = RisoTheme.dimens.lineWidth / 2
    Text(
        text = text,
        textStyle = RisoTheme.typography.heading2,
        modifier = modifier
            .drawBehind {
                drawRect(
                    color = lineColor,
                    size = Size(size.width, lineWidth.toPx()),
                )
            }
            .padding(top = RisoTheme.dimens.lineWidth),
    )
}

@Composable
fun Heading3(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textStyle = RisoTheme.typography.heading3,
        modifier = modifier
    )
}

@Composable
fun Body(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textStyle = RisoTheme.typography.body,
        modifier = modifier
    )
}

@Preview
@Composable
private fun TypographyPreview() {
    RisoTheme {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding()
                .fillMaxSize()
                .risoPaper()
                .risoInk(RisoTheme.colors.inks.vintageBlack)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Heading1(text = "Title Large")
            }
            item {
                Heading2(
                    text = "Title Medium",
                )
            }
            item {
                Heading3(
                    text = "Title Small",
                )
            }
            item {
                Body(
                    text = "Body of text. This is usually pretty long",
                )
            }
        }
    }
}

