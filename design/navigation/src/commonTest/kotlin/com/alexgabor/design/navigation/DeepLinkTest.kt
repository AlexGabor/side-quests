package com.alexgabor.design.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
