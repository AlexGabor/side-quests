package com.alexgabor.lib.appstateurl

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An [AppUrl] that remembers what was written to it, for a test to read back — and that a test can
 * move itself, standing in for the user pressing Back.
 *
 * Merges the way a real one does, so what [parameters] reads is what a screen would have seen.
 */
class FakeAppUrl(initial: LaunchParameters = LaunchParameters.Empty) : AppUrl {

    private val current = MutableStateFlow(initial)

    override val parameters: StateFlow<LaunchParameters> = current.asStateFlow()

    /** Every write, in order, each saying whether it asked for an entry of its own. */
    val writes: List<Write> get() = recorded

    val replaced: List<LaunchParameters> get() = recorded.filterNot { it.asNewEntry }.map { it.parameters }

    val pushed: List<LaunchParameters> get() = recorded.filter { it.asNewEntry }.map { it.parameters }

    /** How many times going back was asked for, whether or not there was anywhere to go. */
    var backs: Int = 0
        private set

    private val recorded = mutableListOf<Write>()

    /** The entries this fake has pushed, the last being the one it is on. */
    private val entries = mutableListOf(initial)

    override fun replace(parameters: LaunchParameters) = write(parameters, asNewEntry = false)

    override fun push(parameters: LaunchParameters) = write(parameters, asNewEntry = true)

    override fun back(): Boolean {
        backs++
        if (entries.size == 1) return false

        entries.removeLast()
        current.value = entries.last()
        return true
    }

    /** As if the user had gone back or forward to an address holding [parameters]. */
    fun moveTo(parameters: LaunchParameters) {
        entries[entries.lastIndex] = parameters
        current.value = parameters
    }

    private fun write(parameters: LaunchParameters, asNewEntry: Boolean) {
        recorded += Write(parameters, asNewEntry)

        val merged = LaunchParameters(current.value.asMap() + parameters.asMap())
        if (asNewEntry) entries += merged else entries[entries.lastIndex] = merged
        current.value = merged
    }

    data class Write(val parameters: LaunchParameters, val asNewEntry: Boolean)
}
