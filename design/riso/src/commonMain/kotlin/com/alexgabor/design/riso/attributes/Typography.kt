package com.alexgabor.design.riso.attributes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import com.alexgabor.design.riso.print.RisoInk
import com.alexgabor.design.riso.print.RisoPrintParams
import com.alexgabor.design.riso.print.risoPrint
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
    )
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

@Preview
@Composable
private fun TypographyPreview() {
    RisoTheme {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding()
                .fillMaxSize()
                .background(RisoTheme.colors.paper)
                .risoPrint(
                    params = RisoPrintParams(
                        inks = listOf(
                            RisoInk(RisoTheme.colors.inks.vintageBlack)
                        )
                    )
                ).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Title Large",
                    textStyle = RisoTheme.typography.heading1,
                )
            }
            item {
                Text(
                    text = "Title Medium",
                    textStyle = RisoTheme.typography.heading2,
                )
            }
            item {
                Text(
                    text = "Title Small",
                    textStyle = RisoTheme.typography.heading3,
                )
            }
            item {
                Text(
                    text = "Body of text. This is usually pretty long",
                    textStyle = RisoTheme.typography.body,
                )
            }
        }
    }
}

