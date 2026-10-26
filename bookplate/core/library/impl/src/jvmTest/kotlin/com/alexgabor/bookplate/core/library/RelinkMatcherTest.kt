package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentEntry
import com.alexgabor.lib.documentaccess.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/** The matching rules on their own: stored files against candidate entries, with durations from a table. */
class RelinkMatcherTest {

    private val durations = mutableMapOf<String, Duration?>()
    private val durationReads = mutableListOf<String>()

    private fun stored(index: Int, path: String, size: Long?, duration: Duration) = StoredFile(index, path, size, duration)

    private fun entry(path: String, size: Long?, duration: Duration? = null): DocumentEntry {
        durations[path] = duration
        return DocumentEntry(DocumentRef("New", path, isFolder = false), path, path.substringAfterLast('/'), size, null)
    }

    private suspend fun match(stored: List<StoredFile>, candidates: List<DocumentEntry>): Matching =
        RelinkMatcher.match(stored, candidates) { durationReads += it.relativePath; durations[it.relativePath] }

    private fun Matching.pathsByIndex(): Map<Int, String> = links.mapValues { it.value.first.relativePath }

    private fun Matching.rules(): Map<Int, MatchRule> = links.mapValues { it.value.second }

    private val book = listOf(
        stored(0, "01 - Opening.mp3", 100, 65.seconds),
        stored(1, "Disc 2/02 - Middle.mp3", 200, 125.seconds),
    )

    @Test
    fun wholeFolderMovedMatchesEveryFileByPathWithoutReadingDurations() = runTest {
        val matching = match(book, listOf(entry("01 - Opening.mp3", 100), entry("Disc 2/02 - Middle.mp3", 200)))

        assertEquals(mapOf(0 to "01 - Opening.mp3", 1 to "Disc 2/02 - Middle.mp3"), matching.pathsByIndex())
        assertEquals(mapOf(0 to MatchRule.RelativePath, 1 to MatchRule.RelativePath), matching.rules())
        assertTrue(matching.unmatched.isEmpty())
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun aRenamedFileIsMatchedBySizeAndDuration() = runTest {
        val matching = match(book, listOf(entry("01 - Intro.mp3", 100, 65.seconds), entry("Disc 2/02 - Middle.mp3", 200)))

        assertEquals(mapOf(0 to "01 - Intro.mp3", 1 to "Disc 2/02 - Middle.mp3"), matching.pathsByIndex())
        assertEquals(MatchRule.SizeAndDuration, matching.rules()[0])
    }

    @Test
    fun aDeletedFileIsUnmatched() = runTest {
        val matching = match(book, listOf(entry("01 - Opening.mp3", 100)))

        assertEquals(mapOf(0 to "01 - Opening.mp3"), matching.pathsByIndex())
        assertEquals(listOf("Disc 2/02 - Middle.mp3"), matching.unmatched.map { it.relativePath })
    }

    @Test
    fun aRenamedAndADeletedFile() = runTest {
        val matching = match(book, listOf(entry("01 - Intro.mp3", 100, 65.seconds)))

        assertEquals(mapOf(0 to "01 - Intro.mp3"), matching.pathsByIndex())
        assertEquals(listOf(1), matching.unmatched.map { it.index })
    }

    @Test
    fun aPathMatchNeedsACompatibleSize() = runTest {
        val matching = match(book.take(1), listOf(entry("01 - Opening.mp3", 999, 65.seconds)))

        assertTrue(matching.links.isEmpty())
    }

    @Test
    fun unknownSizesAreCompatible() = runTest {
        val matching = match(listOf(stored(0, "a.mp3", null, 1.seconds)), listOf(entry("a.mp3", 10)))

        assertEquals(mapOf(0 to "a.mp3"), matching.pathsByIndex())
    }

    @Test
    fun nestedDiscsMovedTogetherMatchByPath() = runTest {
        val discs = listOf(
            stored(0, "Disc 1/01.mp3", 10, 1.seconds),
            stored(1, "Disc 1/02.mp3", 10, 1.seconds),
            stored(2, "Disc 2/01.mp3", 10, 1.seconds),
        )

        val matching = match(discs, listOf(entry("Disc 1/01.mp3", 10), entry("Disc 1/02.mp3", 10), entry("Disc 2/01.mp3", 10)))

        assertEquals(mapOf(0 to "Disc 1/01.mp3", 1 to "Disc 1/02.mp3", 2 to "Disc 2/01.mp3"), matching.pathsByIndex())
    }

    @Test
    fun aRenamedSubFolderMatchesByName() = runTest {
        val discs = listOf(
            stored(0, "Disc 1/01.mp3", 10, 1.seconds),
            stored(1, "Disc 1/02.mp3", 10, 1.seconds),
            stored(2, "Disc 2/01.mp3", 10, 1.seconds),
        )

        val matching = match(discs, listOf(entry("Disc 1/01.mp3", 10), entry("Disc 1/02.mp3", 10), entry("CD 2/01.mp3", 10)))

        assertEquals(mapOf(0 to "Disc 1/01.mp3", 1 to "Disc 1/02.mp3", 2 to "CD 2/01.mp3"), matching.pathsByIndex())
        assertEquals(MatchRule.Name, matching.rules()[2])
    }

    @Test
    fun pathMatchesComeFirstSoANameMatchCantTakeThem() = runTest {
        // Disc 1/01.mp3 is gone; Disc 2/01.mp3 is in place and must stay with its own file.
        val discs = listOf(stored(0, "Disc 1/01.mp3", 10, 1.seconds), stored(1, "Disc 2/01.mp3", 10, 1.seconds))

        val matching = match(discs, listOf(entry("Disc 2/01.mp3", 10)))

        assertEquals(mapOf(1 to "Disc 2/01.mp3"), matching.pathsByIndex())
        assertEquals(listOf(0), matching.unmatched.map { it.index })
    }

    @Test
    fun sameNameDifferentSizePicksTheOneOfItsSize() = runTest {
        val matching = match(
            listOf(stored(0, "01.mp3", 100, 1.seconds)),
            listOf(entry("A/01.mp3", 50), entry("B/01.mp3", 100)),
        )

        assertEquals(mapOf(0 to "B/01.mp3"), matching.pathsByIndex())
        assertEquals(MatchRule.Name, matching.rules()[0])
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun sameNameAndSizeDifferentDurationPicksTheOneOfItsDuration() = runTest {
        val matching = match(
            listOf(stored(0, "01.mp3", 100, 65.seconds)),
            listOf(entry("A/01.mp3", 100, 30.seconds), entry("B/01.mp3", 100, 65.seconds + 200.milliseconds)),
        )

        assertEquals(mapOf(0 to "B/01.mp3"), matching.pathsByIndex())
    }

    @Test
    fun sameNameSizeAndDurationFallsBackToTheParentFolderName() = runTest {
        val matching = match(
            listOf(stored(0, "Disc 2/01.mp3", 100, 65.seconds)),
            listOf(entry("Disc 1/01.mp3", 100, 65.seconds), entry("Moved/Disc 2/01.mp3", 100, 65.seconds)),
        )

        assertEquals(mapOf(0 to "Moved/Disc 2/01.mp3"), matching.pathsByIndex())
    }

    @Test
    fun anAmbiguousNameIsNotGuessed() = runTest {
        val matching = match(
            listOf(stored(0, "Disc 2/01.mp3", 100, 65.seconds)),
            listOf(entry("A/01.mp3", 100, 65.seconds), entry("B/01.mp3", 100, 65.seconds)),
        )

        assertTrue(matching.links.isEmpty())
        assertEquals(listOf(0), matching.unmatched.map { it.index })
    }

    @Test
    fun anAmbiguousSizeAndDurationIsNotGuessed() = runTest {
        val matching = match(
            listOf(stored(0, "01.mp3", 100, 65.seconds)),
            listOf(entry("x.mp3", 100, 65.seconds), entry("y.mp3", 100, 65.seconds)),
        )

        assertTrue(matching.links.isEmpty())
    }

    @Test
    fun sizeAndDurationNeedsAKnownSizeAndASameDuration() = runTest {
        assertTrue(match(listOf(stored(0, "01.mp3", null, 65.seconds)), listOf(entry("x.mp3", 100, 65.seconds))).links.isEmpty())
        assertTrue(match(listOf(stored(0, "01.mp3", 100, 65.seconds)), listOf(entry("x.mp3", 100, 64.seconds))).links.isEmpty())
        assertTrue(match(listOf(stored(0, "01.mp3", 100, Duration.ZERO)), listOf(entry("x.mp3", 100, Duration.ZERO))).links.isEmpty())
        assertTrue(match(listOf(stored(0, "01.mp3", 100, 65.seconds)), listOf(entry("x.mp3", 100, null))).links.isEmpty())
    }

    @Test
    fun aCandidateIsUsedOnce() = runTest {
        val twins = listOf(stored(0, "a.mp3", 100, 65.seconds), stored(1, "b.mp3", 100, 65.seconds))

        val matching = match(twins, listOf(entry("a.mp3", 100, 65.seconds)))

        assertEquals(mapOf(0 to "a.mp3"), matching.pathsByIndex())
        assertEquals(listOf(1), matching.unmatched.map { it.index })
    }

    @Test
    fun namesMatchIgnoringCase() = runTest {
        val matching = match(listOf(stored(0, "Disc 1/Track.MP3", 100, 1.seconds)), listOf(entry("CD/track.mp3", 100)))

        assertEquals(mapOf(0 to "CD/track.mp3"), matching.pathsByIndex())
    }

    @Test
    fun aSingleFileMovedMatchesByItsNameAndARenamedOneBySizeAndDuration() = runTest {
        val single = listOf(stored(0, "Single.m4b", 500, 42.seconds))

        assertEquals(MatchRule.RelativePath, match(single, listOf(entry("Single.m4b", 500))).rules()[0])
        assertEquals(MatchRule.SizeAndDuration, match(single, listOf(entry("Renamed.m4b", 500, 42.seconds))).rules()[0])
        assertTrue(match(single, listOf(entry("Other.m4b", 501, 42.seconds))).links.isEmpty())
    }

    @Test
    fun eachCandidatesDurationIsReadAtMostOnce() = runTest {
        val files = listOf(stored(0, "a.mp3", 100, 65.seconds), stored(1, "b.mp3", 100, 66.seconds))

        match(files, listOf(entry("x.mp3", 100, 65.seconds), entry("y.mp3", 100, 66.seconds)))

        assertEquals(listOf("x.mp3", "y.mp3"), durationReads)
    }
}
