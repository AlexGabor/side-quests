package com.alexgabor.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Serializable
private sealed interface Root : NavKey {
    @Serializable
    data object Home : Root

    @Serializable
    data class Detail(val id: String) : Root
}

@Serializable
private sealed interface Child : NavKey {
    @Serializable
    data object Overview : Child
}

class DeepLinkTest {

    @Test
    fun takeReturnsOnlyThisLevelsKeysInOrder() {
        val deepLink = DeepLink(listOf(Root.Home, Child.Overview, Root.Detail("a")))

        assertEquals(listOf(Root.Home, Root.Detail("a")), deepLink.take<Root>())
        assertEquals(listOf(Child.Overview), deepLink.take<Child>())
    }

    @Test
    fun takeOfAnAbsentTypeIsEmpty() {
        assertTrue(DeepLink(listOf(Root.Home)).take<Child>().isEmpty())
        assertTrue(DeepLink.Empty.take<Root>().isEmpty())
    }

    @Test
    fun restExcludesWhatWasTakenAndNarrowsToEmpty() {
        val deepLink = DeepLink(listOf(Root.Home, Child.Overview))

        assertEquals(listOf(Child.Overview), deepLink.rest<Root>().parts)
        assertSame(DeepLink.Empty, deepLink.rest<Root>().rest<Child>())
    }

    @Test
    fun restLeavesTheOriginalUntouched() {
        val deepLink = DeepLink(listOf(Root.Home, Child.Overview))
        deepLink.rest<Root>()

        assertEquals(listOf(Root.Home, Child.Overview), deepLink.parts)
    }
}

@OptIn(ExperimentalTestApi::class)
class RememberDeepLinkedBackStackTest {

    @Test
    fun seedsTheBackStackFromTheDeepLink() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>

        setContent {
            CompositionLocalProvider(
                LocalDeepLink provides DeepLink(listOf(Root.Home, Root.Detail("a"))),
            ) {
                nav = rememberDeepLinkedBackStack(Root.serializer()) { listOf(Root.Home) }
            }
        }
        waitForIdle()

        assertEquals(listOf(Root.Home, Root.Detail("a")), nav.backStack.toList())
    }

    @Test
    fun fallsBackToInitialWhenTheDeepLinkHoldsNothingForThisLevel() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>

        setContent {
            CompositionLocalProvider(LocalDeepLink provides DeepLink(listOf(Child.Overview))) {
                nav = rememberDeepLinkedBackStack(Root.serializer()) { listOf(Root.Home) }
            }
        }
        waitForIdle()

        assertEquals(listOf(Root.Home), nav.backStack.toList())
    }

    @Test
    fun handsTheRemainderToTheDisplaysBelow() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>

        setContent {
            CompositionLocalProvider(
                LocalDeepLink provides DeepLink(listOf(Root.Home, Child.Overview)),
            ) {
                nav = rememberDeepLinkedBackStack(Root.serializer()) { listOf(Root.Home) }
            }
        }
        waitForIdle()

        assertEquals(listOf(Child.Overview), nav.remainder.parts)
    }

    @Test
    fun aRestoredBackStackWinsOverTheDeepLink() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>
        val restoration = Restoration()

        setContent {
            // Still provided after the restore, because Android hands the same intent back when it
            // recreates the process — the restored stack has to survive it.
            CompositionLocalProvider(
                LocalDeepLink provides DeepLink(listOf(Root.Home, Root.Detail("a"))),
            ) {
                restoration.Content {
                    nav = rememberDeepLinkedBackStack(Root.serializer()) { listOf(Root.Home) }
                }
            }
        }
        waitForIdle()

        // What the user did after the deep link opened.
        nav.backStack.removeLastOrNull()
        nav.backStack.add(Root.Detail("b"))
        waitForIdle()

        restoration.emulateProcessDeath(this)

        assertEquals(listOf(Root.Home, Root.Detail("b")), nav.backStack.toList())
    }

    @Test
    fun aRestoredLevelHandsNothingDown() = runComposeUiTest {
        lateinit var nav: DeepLinkedBackStack<Root>
        val restoration = Restoration()

        setContent {
            CompositionLocalProvider(
                LocalDeepLink provides DeepLink(listOf(Root.Home, Child.Overview)),
            ) {
                restoration.Content {
                    nav = rememberDeepLinkedBackStack(Root.serializer()) { listOf(Root.Home) }
                }
            }
        }
        waitForIdle()
        assertEquals(listOf(Child.Overview), nav.remainder.parts)

        restoration.emulateProcessDeath(this)

        // Nothing is left for a nested display to act on, so a stale link can't navigate for the
        // user after a restore.
        assertTrue(nav.remainder.isEmpty)
    }
}

/**
 * Saves and restores its content the way the platform would.
 *
 * Hand-rolled because `StateRestorationTester` is still a `TODO()` on skiko, and the JVM is where
 * these tests run.
 */
@OptIn(ExperimentalTestApi::class)
private class Restoration {

    private var saved: Map<String, List<Any?>>? = null
    private var generation by mutableStateOf(0)
    private var present by mutableStateOf(true)
    private var registry: SaveableStateRegistry? = null

    @Composable
    fun Content(content: @Composable () -> Unit) {
        val current = remember(generation) {
            SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        }
        registry = current

        CompositionLocalProvider(LocalSaveableStateRegistry provides current) {
            // Taking the content out of composition and putting it back is what makes every
            // `remember` inside run afresh, leaving only what the registry saved. It has to be the
            // same call site both times: `rememberSaveable` derives its key from the composite key
            // hash, so wrapping this in `key(generation)` would change the key and restore nothing.
            if (present) content()
        }
    }

    fun emulateProcessDeath(test: ComposeUiTest) {
        saved = requireNotNull(registry).performSave()

        present = false
        test.waitForIdle()

        generation++
        present = true
        test.waitForIdle()
    }
}
