package com.alexgabor.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.launch.LaunchParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/**
 * Where the app was asked to open, as a flat spine of keys — one or more per level of navigation.
 *
 * Deliberately immutable. Each level [take]s the key type it owns and passes the [rest] to its
 * children, rather than draining a shared list: a `remember` block can run in a composition that is
 * then abandoned, and a mutation made there is never rolled back, which would lose the deep link
 * for good. Handing a narrowed copy downwards also means a subtree can only ever see what its
 * parent gave it, so scoping is structural instead of bookkeeping.
 */
@Immutable
class DeepLink(val parts: List<NavKey>) {

    val isEmpty: Boolean get() = parts.isEmpty()

    /** This level's slice, in the order the router wrote it. */
    inline fun <reified T : NavKey> take(): List<T> = parts.filterIsInstance<T>()

    /** What is left once this level has taken its own key type. */
    inline fun <reified T : NavKey> rest(): DeepLink {
        val remaining = parts.filterNot { it is T }
        return if (remaining.isEmpty()) Empty else DeepLink(remaining)
    }

    companion object {
        val Empty = DeepLink(emptyList())
    }
}

/** Immutable, so sharing one default instance process-wide is harmless. */
val LocalDeepLink = staticCompositionLocalOf { DeepLink.Empty }

/** The key a screen goes by in an address, and what [DeepLink] readers look for. */
const val ScreenParameter = "screen"

/**
 * A back stack and whatever the deep link still holds for the displays nested below it — kept
 * saying the same thing as the app's address, where the platform has one.
 *
 * Navigating pushes: a screen is somewhere the user went, and Back is expected to undo it. Which
 * means the address can be moved by the user too — that is what the browser's Back button is — so
 * this reads in both directions. Nothing here knows any screen by name; [screenName] and
 * [keyForScreen] are how the app says what its own screens are called, and the two are expected to
 * be inverses of each other.
 *
 * @param screenName the word this key goes by, matched without regard to case.
 * @param keyForScreen the key to open for a word, or null for one this level doesn't know.
 */
class DeepLinkedBackStack<T : NavKey>(
    val backStack: NavBackStack<T>,
    val remainder: DeepLink,
    private val appUrl: AppUrl,
    private val screenName: (T) -> String,
    private val keyForScreen: (String) -> T?,
) {
    /** The screen underneath everything, which is what an address naming none of them means. */
    private val rootScreen: String? get() = backStack.firstOrNull()?.let(::nameOf)

    fun open(key: T) {
        backStack.add(key)
    }

    /**
     * Where the address has an entry of its own to go back to, going back is its move to make and
     * the screen leaves when it says so — so that the app's own back button and the platform's are
     * one move rather than two.
     */
    fun back() {
        if (appUrl.back()) return
        popScreen()
    }

    /** Keeps the back stack and the address saying the same thing, for as long as it is collected. */
    suspend fun sync(): Unit = coroutineScope {
        launch { writeScreenToUrl() }
        launch { followUrl() }
    }

    /**
     * The screen the app opened on is skipped: it is already what the address says, and writing it
     * would put an entry in front of wherever the user came from.
     */
    private suspend fun writeScreenToUrl() {
        snapshotFlow { backStack.lastOrNull()?.let(::nameOf) }
            .distinctUntilChanged()
            .drop(1)
            .collect { screen ->
                // An address already saying this is one the app is here because of — the user
                // moved it. Writing it back would be a second entry for one move, and pushing
                // after a Back throws away everything ahead of it, so Forward would stop working.
                if (screen != null && screen != addressedScreen()) {
                    appUrl.push(LaunchParameters(mapOf(ScreenParameter to screen)))
                }
            }
    }

    /**
     * The address as it stands is skipped in the same way: the back stack was seeded from it, and
     * a restored one is the more complete answer of the two.
     *
     * Only the screen is read back. Whatever else a screen writes about itself it writes in place,
     * so two entries never differ by anything else, and re-reading the rest would undo edits the
     * user made after arriving.
     */
    private suspend fun followUrl() {
        appUrl.parameters
            .map { it[ScreenParameter] }
            .distinctUntilChanged()
            .drop(1)
            .collect { screen -> goTo(screen?.lowercase() ?: rootScreen) }
    }

    private fun goTo(screen: String?) {
        if (screen == null || screen == backStack.lastOrNull()?.let(::nameOf)) return

        val found = backStack.indexOfLast { nameOf(it) == screen }
        if (found >= 0) {
            // Already behind us: this was a Back, however many screens it went.
            while (backStack.size > found + 1) popScreen()
        } else {
            keyForScreen(screen)?.let(backStack::add)
        }
    }

    /** Leaves a screen without telling the address, for when the address is what asked. */
    private fun popScreen() {
        backStack.removeLastOrNull()
    }

    private fun addressedScreen(): String? =
        appUrl.parameters.value[ScreenParameter]?.lowercase() ?: rootScreen

    private fun nameOf(key: T): String = screenName(key).lowercase()
}

/**
 * A back stack seeded from this level's slice of [LocalDeepLink], falling back to [initial], and
 * thereafter kept in step with [appUrl].
 *
 * The single guard that makes deep links safe lives here rather than at each call site: a saved
 * `applied` flag. Restoring a back stack skips [rememberSerializable]'s initializer entirely, so
 * without it the re-delivered launch input — Android hands the same intent back after process
 * death — would still be sitting there for the next display to pick up and act on, long after the
 * user had navigated somewhere else.
 *
 * @param appUrl where this level writes which screen is on top. A level whose screens have no
 * address of their own — anything nested — is given one that does nothing.
 */
@Composable
inline fun <reified T : NavKey> rememberDeepLinkedBackStack(
    serializer: KSerializer<T>,
    appUrl: AppUrl,
    noinline screenName: (T) -> String,
    noinline keyForScreen: (String) -> T?,
    noinline initial: () -> List<T>,
): DeepLinkedBackStack<T> {
    val deepLink = LocalDeepLink.current

    // False only on a genuinely fresh start. Saved, so a restored back stack is the real state and
    // the launch input is not applied a second time on top of it.
    var applied by rememberSaveable { mutableStateOf(false) }
    val slice = if (applied) emptyList() else deepLink.take<T>()

    val backStack = rememberSerializable(serializer = NavBackStackSerializer(serializer)) {
        // `ifEmpty` is what keeps this total: NavDisplay rejects an empty back stack, so an absent
        // or unrecognized deep link falls through to this level's own default.
        NavBackStack(*slice.ifEmpty(initial).toTypedArray())
    }

    // After the composition is applied, never during it — an abandoned one must not mark the link
    // as used.
    SideEffect { applied = true }

    // Remembered so the value handed downwards is stable for the life of this level, rather than
    // flipping to Empty on the recomposition that follows `applied` being set.
    val remainder = remember { if (applied) DeepLink.Empty else deepLink.rest<T>() }

    // Not keyed on the two mappings: they say what an app's screens are called, which doesn't
    // change while it runs, and a lambda written at the call site is a new object every time.
    val nav = remember(backStack, remainder, appUrl) {
        DeepLinkedBackStack(backStack, remainder, appUrl, screenName, keyForScreen)
    }

    LaunchedEffect(nav) { nav.sync() }

    return nav
}
