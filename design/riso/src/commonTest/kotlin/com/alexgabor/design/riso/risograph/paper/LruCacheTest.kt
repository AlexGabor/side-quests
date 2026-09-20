package com.alexgabor.design.riso.risograph.paper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LruCacheTest {

    @Test
    fun holdsWhatWasPut() {
        val cache = LruCache<String, Any>(maxEntries = 2)
        val value = Any()
        assertSame(value, cache.getOrPut("a") { value })
        assertSame(value, cache["a"])
        // Already held, so the second value is never made.
        assertSame(value, cache.getOrPut("a") { error("created twice") })
    }

    @Test
    fun evictsTheLeastRecentlyUsed() {
        val cache = LruCache<String, Int>(maxEntries = 2)
        cache.getOrPut("a") { 1 }
        cache.getOrPut("b") { 2 }
        // Touching a makes b the eldest.
        assertEquals(1, cache["a"])
        cache.getOrPut("c") { 3 }

        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }

    @Test
    fun aRaceOnOneKeyHandsEveryoneTheSameValue() {
        val cache = LruCache<String, Any>(maxEntries = 4)
        val first = Any()
        // A create that loses the race — the key lands while it runs — is dropped for the winner.
        val winner = cache.getOrPut("a") {
            cache.getOrPut("a") { first }
            Any()
        }
        assertSame(first, winner)
        assertSame(first, cache["a"])
    }
}
