package com.alexgabor.design.riso.components


internal const val IconViewport = 24f

internal val BackIconPaths = listOf(
    "M19,12L5,12",
    "M11,6L5,12L11,18",
)

internal val SettingsIconPaths = listOf(
    "M7,6L7,13",
    "M7,14.5m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0",
    "M7,16L7,18",
    "M12,6L12,8",
    "M12,9.5m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0",
    "M12,11L12,18",
    "M17,6L17,11",
    "M17,12.5m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0",
    "M17,14L17,18",
)

/** Each platform's own share glyph, so the action reads as the one the user already knows. */
internal expect val ShareIconPaths: List<String>

/** Outlined, and sat left of centre: a triangle's mass is in its base, so this is where it looks centred. */
internal val PlayIconPaths = listOf(
    "M7.5,5L18.5,12L7.5,19Z",
)

internal val PauseIconPaths = listOf(
    "M9,6L9,18",
    "M15,6L15,18",
)

/** The "10" inside both skip icons. Declared first: the icons below are built from it as the file loads. */
private val TenDigitsPaths = listOf(
    "M8.4,11.2L9.6,10.5L9.6,15.5",
    "M13.9,10.5a1.4,2.5 0,1 1,0,5a1.4,2.5 0,1 1,0,-5",
)

/**
 * An arrow circling back round a "10". The ring has a radius of 7 about (12, 13), opens at ten
 * o'clock, and its head sits at twelve, pointing the way the ring turns.
 */
internal val Rewind10IconPaths = listOf(
    "M12,6A7,7 0,1 1,5.94,9.5",
    "M14.5,3.5L12,6L14.5,8.5",
) + TenDigitsPaths

/** [Rewind10IconPaths] mirrored, the "10" left reading the right way round. */
internal val Forward10IconPaths = listOf(
    "M12,6A7,7 0,1 0,18.06,9.5",
    "M9.5,3.5L12,6L9.5,8.5",
) + TenDigitsPaths

/** Three bulleted lines, spanning the same 5 to 19 as [BackIconPaths]. */
internal val ChaptersIconPaths = listOf(
    "M5,7L6,7",
    "M10,7L19,7",
    "M5,12L6,12",
    "M10,12L19,12",
    "M5,17L6,17",
    "M10,17L19,17",
)

/** A dial open at the bottom, with its needle past halfway. */
internal val SpeedIconPaths = listOf(
    "M5.5,17A7,7 0,1 1,18.5,17",
    "M12,14.4L15.5,10.5",
)

internal val AddIconPaths = listOf(
    "M12,5L12,19",
    "M5,12L19,12",
)

/** The lens and handle span the same 5 to 19 as [BackIconPaths]. */
internal val SearchIconPaths = listOf(
    "M10.5,10.5m-5.5,0a5.5,5.5 0,1 1,11 0a5.5,5.5 0,1 1,-11 0",
    "M14.5,14.5L19,19",
)

internal val CloseIconPaths = listOf(
    "M6,6L18,18",
    "M18,6L6,18",
)
