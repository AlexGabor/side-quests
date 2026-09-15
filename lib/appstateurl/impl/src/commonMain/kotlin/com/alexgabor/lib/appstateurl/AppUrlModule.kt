package com.alexgabor.lib.appstateurl

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module

/** Binds the [AppUrl] of whichever platform this is built for. */
expect val appUrlModule: Module

/**
 * For the platforms where there is no address to write: everything a phone or a desktop window
 * runs on.
 *
 * A do-nothing implementation rather than no binding at all, so a screen can ask for an [AppUrl]
 * the same way everywhere and say what it is doing without checking who is listening.
 */
object NoAppUrl : AppUrl {

    override val parameters: StateFlow<LaunchParameters> = MutableStateFlow(LaunchParameters.Empty)

    override fun replace(parameters: LaunchParameters) = Unit

    override fun push(parameters: LaunchParameters) = Unit

    /** Nothing was ever pushed here, so there is nothing to go back to. */
    override fun back(): Boolean = false
}
