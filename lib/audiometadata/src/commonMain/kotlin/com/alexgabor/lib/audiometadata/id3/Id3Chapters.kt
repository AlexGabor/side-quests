package com.alexgabor.lib.audiometadata.id3

import com.alexgabor.lib.audiometadata.Chapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collects `CHAP` and `CTOC` frames from every tag, then builds the chapter tree once the duration is
 * known.
 */
internal class Id3Chapters {

    class Chap(val id: String, val title: String?, val startMs: Long, val endMs: Long)

    class Toc(val id: String, val title: String?, val topLevel: Boolean, val ordered: Boolean, val children: List<String>)

    private val chaps = LinkedHashMap<String, Chap>()
    private val tocs = LinkedHashMap<String, Toc>()

    /** The first frame with a given element ID wins, as the first tag wins for everything else. */
    fun add(chap: Chap) {
        if (chap.id !in chaps) chaps[chap.id] = chap
    }

    fun add(toc: Toc) {
        if (toc.id !in tocs) tocs[toc.id] = toc
    }

    /**
     * Chapters from the top-level table of contents (or one no other table lists). Without a usable one,
     * every `CHAP` in start order.
     */
    fun build(duration: Duration?): List<Chapter> {
        if (chaps.isEmpty()) return emptyList()
        val leaves = leaves(duration)
        val root = tocs.values.firstOrNull { it.topLevel }
            ?: tocs.values.firstOrNull { toc -> tocs.values.none { toc.id in it.children } }
        val tree = root?.let { entries(it, leaves, setOf(it.id), depth = 0) }.orEmpty()
        return tree.ifEmpty { leaves.values.sortedBy { it.start } }
    }

    /**
     * Every `CHAP` as a chapter, its end fixed when it isn't after its start: the next chapter's start,
     * or else the duration.
     */
    private fun leaves(duration: Duration?): Map<String, Chapter> {
        val sorted = chaps.values.sortedBy { it.startMs }
        val result = LinkedHashMap<String, Chapter>()
        sorted.forEachIndexed { index, chap ->
            val end = when {
                chap.endMs > chap.startMs -> chap.endMs.milliseconds
                else -> sorted.drop(index + 1).firstOrNull { it.startMs > chap.startMs }?.startMs?.milliseconds
                    ?: duration?.takeIf { it > chap.startMs.milliseconds }
                    ?: chap.startMs.milliseconds
            }
            result[chap.id] = Chapter(chap.title, chap.startMs.milliseconds, end)
        }
        return result
    }

    private fun entries(toc: Toc, leaves: Map<String, Chapter>, path: Set<String>, depth: Int): List<Chapter> {
        if (depth > MAX_DEPTH) return emptyList()
        val children = toc.children.mapNotNull { id ->
            leaves[id] ?: tocs[id]?.takeIf { id !in path }?.let { child ->
                val nested = entries(child, leaves, path + id, depth + 1)
                if (nested.isEmpty()) null else Chapter(child.title, nested.minOf { it.start }, nested.maxOf { it.end }, nested)
            }
        }
        return if (toc.ordered) children else children.sortedBy { it.start }
    }

    private companion object {
        const val MAX_DEPTH = 16
    }
}
