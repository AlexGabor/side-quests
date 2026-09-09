package com.alexgabor.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
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

/**
 * A back stack and whatever the deep link still holds for the displays nested below it.
 */
class DeepLinkedBackStack<T : NavKey>(
    val backStack: NavBackStack<T>,
    val remainder: DeepLink,
)

/**
 * A back stack seeded from this level's slice of [LocalDeepLink], falling back to [initial].
 *
 * The single guard that makes deep links safe lives here rather than at each call site: a saved
 * `applied` flag. Restoring a back stack skips [rememberSerializable]'s initializer entirely, so
 * without it the re-delivered launch input — Android hands the same intent back after process
 * death — would still be sitting there for the next display to pick up and act on, long after the
 * user had navigated somewhere else.
 */
@Composable
inline fun <reified T : NavKey> rememberDeepLinkedBackStack(
    serializer: KSerializer<T>,
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

    return remember(backStack, remainder) { DeepLinkedBackStack(backStack, remainder) }
}
