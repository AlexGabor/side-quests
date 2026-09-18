package com.alexgabor.lib.share

import org.koin.core.module.Module

/** Binds the [Share] of whichever platform this is built for. */
expect val shareModule: Module

/**
 * For the platforms with no share sheet: desktop windows and the browser.
 *
 * A do-nothing implementation rather than no binding at all, so a screen can ask for a [Share] the
 * same way everywhere and read [isAvailable] instead of checking the platform.
 */
object NoShare : Share {

    override val isAvailable: Boolean = false

    override fun text(text: String) = Unit
}
