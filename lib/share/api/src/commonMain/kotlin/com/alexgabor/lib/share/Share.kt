package com.alexgabor.lib.share

/**
 * The platform's share sheet — the system chooser on Android, the activity sheet on iOS — and
 * nothing at all where there isn't one.
 */
interface Share {

    /** Whether there is a share sheet to hand anything to. A screen hides its share action when not. */
    val isAvailable: Boolean

    /** Opens the share sheet with [text] as plain text. Does nothing when not [isAvailable]. */
    fun text(text: String)
}
