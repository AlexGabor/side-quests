package com.alexgabor.design.riso.attributes

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalColors = staticCompositionLocalOf { RisoColors }

val RisoColors = Colors()

@Immutable
data class Colors(
    val inks: Inks = Inks(),
    val paper: Color = Color(0xFFefebe1),
    val content: Color = inks.vintageBlack,
    val accent: Color = inks.purple,
)

/**
 * Twelve of the most commonly stocked RISO drum inks, spread around the colour wheel.
 *
 * Hex values are the published approximations of the real inks. They are only ever approximate —
 * RISO inks are spot colours that do not sit inside sRGB, and the fluorescents in particular print
 * considerably brighter than any screen can show.
 */
@Immutable
data class Inks(
    val fluorescentPink: Color = Color(0xFFFF48B0),
    val red: Color = Color(0xFFFF665E),
    val orange: Color = Color(0xFFFF6C2F),
    val yellow: Color = Color(0xFFFFE800),
    val green: Color = Color(0xFF00A95C),
    val teal: Color = Color(0xFF00838A),
    val aqua: Color = Color(0xFF5EC8E5),
    val blue: Color = Color(0xFF0078BF),
    val federalBlue: Color = Color(0xFF3D5588),
    val purple: Color = Color(0xFF765BA7),
    val burgundy: Color = Color(0xFF914E72),
    val vintageBlack: Color = Color(0xFF383226),
) {
    /** Every ink with its RISO name, in colour-wheel order — for pickers and swatch charts. */
    val all: List<NamedInk> = listOf(
        NamedInk("Fluorescent Pink", fluorescentPink),
        NamedInk("Red", red),
        NamedInk("Orange", orange),
        NamedInk("Yellow", yellow),
        NamedInk("Green", green),
        NamedInk("Teal", teal),
        NamedInk("Aqua", aqua),
        NamedInk("Blue", blue),
        NamedInk("Federal Blue", federalBlue),
        NamedInk("Purple", purple),
        NamedInk("Burgundy", burgundy),
        NamedInk("Black", vintageBlack),
    )
}

/** A RISO ink paired with the name it is ordered by. */
@Immutable
data class NamedInk(val name: String, val color: Color)
