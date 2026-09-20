package com.alexgabor.design.riso.risograph.paper

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A small least-recently-used map, safe to share between threads.
 *
 * Held as an immutable snapshot swapped under compare-and-set rather than a `LinkedHashMap` under a
 * lock, because it is shared with iOS and the browser, where neither `removeEldestEntry` nor
 * `@Synchronized` exists. [getOrPut] never runs [create] inside the swap: two threads racing on one
 * key may both create a value, and the first to land is the one both get back.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class LruCache<K : Any, V : Any>(private val maxEntries: Int) {

    /** Keys in least-recently-used order: [Entries.order] `[0]` is the next one evicted. */
    private class Entries<K, V>(val map: Map<K, V>, val order: List<K>)

    private val entries = AtomicReference(Entries<K, V>(emptyMap(), emptyList()))

    operator fun get(key: K): V? {
        val held = entries.load()
        val value = held.map[key] ?: return null
        touch(held, key)
        return value
    }

    /** The value held for [key], or [create]'s — whichever reached the map first. */
    fun getOrPut(key: K, create: () -> V): V {
        get(key)?.let { return it }
        val value = create()
        while (true) {
            val held = entries.load()
            held.map[key]?.let { return it }
            val order = held.order + key
            val next = if (order.size > maxEntries) {
                Entries(held.map - order.first() + (key to value), order.drop(1))
            } else {
                Entries(held.map + (key to value), order)
            }
            if (entries.compareAndSet(held, next)) return value
        }
    }

    /**
     * Moves [key] to the most-recently-used end. Best effort: a lost swap leaves one entry with a
     * staler place in the eviction order, which evicts slightly less well rather than wrongly.
     */
    private fun touch(held: Entries<K, V>, key: K) {
        if (held.order.lastOrNull() == key) return
        entries.compareAndSet(held, Entries(held.map, held.order - key + key))
    }
}
