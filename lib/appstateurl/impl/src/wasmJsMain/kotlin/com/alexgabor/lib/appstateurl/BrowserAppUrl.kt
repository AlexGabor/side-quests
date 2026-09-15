package com.alexgabor.lib.appstateurl

import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appUrlModule: Module = module {
    single<AppUrl> { BrowserAppUrl() }
}

/**
 * The page url as the app's address.
 *
 * Only the query is written, and the fragment is dropped rather than carried along: a launch reads
 * the fragment over the query, so one left over from the link the page was opened with would
 * outrank everything written afterwards.
 */
class BrowserAppUrl : AppUrl {

    private val current = MutableStateFlow(currentParameters())

    override val parameters: StateFlow<LaunchParameters> = current.asStateFlow()

    init {
        // Back and forward change the url without reloading, so this is the only way to hear about
        // it. `replaceState` and `pushState` deliberately do not fire it — those are our own doing,
        // and [current] is updated in hand with them.
        addPopStateListener { current.value = currentParameters() }
    }

    override fun replace(parameters: LaunchParameters) = write(parameters, asNewEntry = false)

    override fun push(parameters: LaunchParameters) = write(parameters, asNewEntry = true)

    /**
     * Only ever goes back as far as the entry the page was opened on: the one before that belongs
     * to wherever the user came from, and leaving the site is not what a Back button in the app
     * means.
     *
     * How far that is, is written into each entry as it is pushed. The history itself cannot be
     * read — how many entries are behind, or whose they are, is deliberately none of a page's
     * business — but what a page put in its own entries comes back to it.
     */
    override fun back(): Boolean {
        if (depth() == 0) return false
        historyBack()
        return true
    }

    private fun write(parameters: LaunchParameters, asNewEntry: Boolean) {
        val merged = LaunchParameters(current.value.asMap() + parameters.asMap())
        // An address that already says this needs no writing, and an entry identical to the one
        // before it would be a Back that appears to do nothing.
        if (merged == current.value) return

        val query = merged.toQueryString()
        val url = if (query.isEmpty()) locationPathname() else "${locationPathname()}?$query"
        // Replacing stays at the depth it is at: it rewrites this entry rather than adding one.
        if (asNewEntry) pushUrl(url, depth() + 1) else replaceUrl(url, depth())

        current.value = merged
    }

    private fun currentParameters(): LaunchParameters =
        LaunchParameters.ofQueryString(locationSearch())
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationSearch(): String = js("window.location.search")

@OptIn(ExperimentalWasmJsInterop::class)
private fun locationPathname(): String = js("window.location.pathname")

/** How many entries this page has pushed of its own, as written into the entry it is on. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun depth(): Int = js("(window.history.state && window.history.state.appUrlDepth) || 0")

@OptIn(ExperimentalWasmJsInterop::class)
private fun replaceUrl(url: String, depth: Int): Unit =
    js("window.history.replaceState({ appUrlDepth: depth }, '', url)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun pushUrl(url: String, depth: Int): Unit =
    js("window.history.pushState({ appUrlDepth: depth }, '', url)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun historyBack(): Unit = js("window.history.back()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun addPopStateListener(onPopState: () -> Unit): Unit =
    js("window.addEventListener('popstate', onPopState)")
