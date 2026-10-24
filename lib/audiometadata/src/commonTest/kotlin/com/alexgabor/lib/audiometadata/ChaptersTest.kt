package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ChaptersTest {

    private fun chapters(tag: ByteArray, audio: ByteArray = Mpeg.frames(2)) = readMp3(tag + audio).chapters

    private fun leaf(title: String?, startS: Int, endS: Int) = Chapter(title, startS.seconds, endS.seconds)

    @Test
    fun flatTableOfContents() {
        for (major in listOf(3, 4)) {
            val tag = Id3Builder(major)
                .ctoc("toc", listOf("c1", "c2"), topLevel = true)
                .chap("c1", 0, 10_000, "One")
                .chap("c2", 10_000, 20_000, "Two")
                .build()
            assertEquals(listOf(leaf("One", 0, 10), leaf("Two", 10, 20)), chapters(tag), "v2.$major")
        }
    }

    @Test
    fun nestedSections() {
        val tag = Id3Builder(4)
            .chap("c1", 0, 10_000, "One")
            .chap("c2", 10_000, 20_000, "Two")
            .chap("c3", 20_000, 30_000, "Three")
            .ctoc("p2", listOf("c3"), title = "Part 2")
            .ctoc("root", listOf("p1", "p2"), topLevel = true)
            .ctoc("p1", listOf("c1", "c2"), title = "Part 1")
            .build()
        assertEquals(
            listOf(
                Chapter("Part 1", 0.seconds, 20.seconds, listOf(leaf("One", 0, 10), leaf("Two", 10, 20))),
                Chapter("Part 2", 20.seconds, 30.seconds, listOf(leaf("Three", 20, 30))),
            ),
            chapters(tag),
        )
    }

    @Test
    fun rootWithoutTopLevelFlagIsTheUnreferencedOne() {
        val tag = Id3Builder(3)
            .ctoc("child", listOf("c2"), title = "Sub")
            .ctoc("root", listOf("c1", "child"))
            .chap("c1", 0, 1_000, "A")
            .chap("c2", 1_000, 2_000, "B")
            .build()
        assertEquals(
            listOf(leaf("A", 0, 1), Chapter("Sub", 1.seconds, 2.seconds, listOf(leaf("B", 1, 2)))),
            chapters(tag),
        )
    }

    @Test
    fun unorderedEntriesAreSortedByStart() {
        val tag = Id3Builder(3)
            .ctoc("toc", listOf("b", "a"), topLevel = true, ordered = false)
            .chap("a", 0, 1_000, "A")
            .chap("b", 1_000, 2_000, "B")
            .build()
        assertEquals(listOf("A", "B"), chapters(tag).map { it.title })
    }

    @Test
    fun withoutTableOfContentsChaptersAreFlatInStartOrder() {
        val tag = Id3Builder(4)
            .chap("b", 5_000, 9_000, "B")
            .chap("a", 0, 5_000)
            .build()
        assertEquals(listOf(leaf(null, 0, 5), leaf("B", 5, 9)), chapters(tag))
    }

    @Test
    fun unknownChildrenAndCyclesAreSkipped() {
        val tag = Id3Builder(3)
            .ctoc("a", listOf("b", "missing", "c1"), topLevel = true)
            .ctoc("b", listOf("a", "c2"), title = "B")
            .chap("c1", 0, 1_000, "One")
            .chap("c2", 1_000, 2_000, "Two")
            .build()
        assertEquals(
            listOf(Chapter("B", 1.seconds, 2.seconds, listOf(leaf("Two", 1, 2))), leaf("One", 0, 1)),
            chapters(tag),
        )
    }

    @Test
    fun pureCycleFallsBackToFlatList() {
        val tag = Id3Builder(3)
            .ctoc("a", listOf("b"))
            .ctoc("b", listOf("a"))
            .chap("c1", 0, 1_000, "One")
            .build()
        assertEquals(listOf(leaf("One", 0, 1)), chapters(tag))
    }

    @Test
    fun missingEndsAreFixed() {
        // 10 frames of 1152 samples at 44.1 kHz: about 261 ms.
        val tag = Id3Builder(3)
            .chap("c1", 0, 0, "One")
            .chap("c2", 100, 50, "Two")
            .build()
        val result = chapters(tag, Mpeg.xing(10) + Mpeg.frames(9))
        assertEquals(100.milliseconds, result[0].end)
        assertEquals((10 * 1152 * 1_000_000L / 44100).toDouble(), result[1].end.inWholeMicroseconds.toDouble(), 1.0)
    }

    @Test
    fun emptyWhenThereAreNoChapters() {
        assertEquals(emptyList(), chapters(Id3Builder(3).ctoc("toc", listOf("x"), topLevel = true).build()))
    }
}
