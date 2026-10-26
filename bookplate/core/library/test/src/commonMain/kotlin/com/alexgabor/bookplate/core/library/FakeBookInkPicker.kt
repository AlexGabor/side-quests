package com.alexgabor.bookplate.core.library

/**
 * [BookInkPicker] that returns [pairs] in order, starting over after the last one. [picked] counts the pairs given.
 */
class FakeBookInkPicker(
    private val pairs: List<BookInks> = listOf(
        BookInks(accent = "Teal", accentVariant = "Yellow"),
        BookInks(accent = "Red", accentVariant = "Blue"),
        BookInks(accent = "Green", accentVariant = "Orange"),
    ),
) : BookInkPicker {

    var picked: Int = 0
        private set

    override fun pick(): BookInks = pairs[picked++ % pairs.size]
}
