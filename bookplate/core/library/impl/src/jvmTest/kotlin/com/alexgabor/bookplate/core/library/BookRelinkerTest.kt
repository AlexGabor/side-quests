package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/** Relinking through the real library: Room, the importer, availability and the matcher, over the fake documents. */
class BookRelinkerTest {

    private val rule = TestScopeRule()
    private lateinit var library: LibraryHarness

    private val documents get() = library.documents
    private val relinker get() = library.relinker

    @BeforeTest
    fun setUp() {
        rule.install()
        library = LibraryHarness()
    }

    @AfterTest
    fun tearDown() {
        library.close()
        rule.reset()
    }

    /** `Opening` (1 s, tagged) and `Disc 2/Middle` (2 s, untagged, another size), with a folder cover. */
    private fun addSampleBook(at: String = "Books/Sample Book") {
        documents.addFile("$at/01 - Opening.mp3", taggedMp3("Opening", artist = "Sample Author", album = "Sample Book"))
        documents.addFile("$at/Disc 2/02 - Middle.mp3", untaggedMp3())
        documents.addFile("$at/cover.jpg", fixture("mp3/cover.png"))
        documents.addFile("$at/notes.txt", "Not audio.".encodeToByteArray())
    }

    /** Three tagged files of the same size and duration, 1 s each. */
    private fun addThreePartBook(at: String) {
        documents.addFile("$at/01 - One.mp3", taggedMp3("One", album = "Three"))
        documents.addFile("$at/02 - Two.mp3", taggedMp3("Two", album = "Three"))
        documents.addFile("$at/03 - Three.mp3", taggedMp3("Three", album = "Three"))
    }

    /** A sample book that has been played: position, speed and last played set, as playback would. */
    private suspend fun playedSampleBook(): BookId {
        addSampleBook()
        val id = library.import("Books/Sample Book")
        library.repository.setPosition(id, 2.seconds)
        library.repository.setSpeed(id, 1.5f)
        library.repository.setLastPlayed(id, library.clock.now.plus(60.seconds))
        return id
    }

    /** What the database holds for [id], as raw rows, so "nothing changed" compares everything. */
    private suspend fun rows(id: BookId) = assertNotNull(library.database.dao().bookNow(id.value)).let {
        it.copy(files = it.files.sortedBy { file -> file.ordinal }, chapters = it.chapters.sortedBy { chapter -> chapter.ordinal })
    }

    /** Everything but the references and paths: what a relink must keep. */
    private fun assertKept(before: BookDetails, after: BookDetails) {
        assertEquals(before.book.copy(source = after.book.source), after.book)
        assertEquals(before.chapters, after.chapters)
        assertEquals(before.files.map { Triple(it.index, it.start, it.duration) }, after.files.map { Triple(it.index, it.start, it.duration) })
    }

    @Test
    fun wholeFolderMovedIsAFullMatchKeepingPositionSpeedChaptersCoverAndTimes() = rule.testScope.runTest {
        val id = playedSampleBook()
        val before = library.details(id)
        documents.rename("Books/Sample Book", "Elsewhere/Moved Book")
        assertEquals(Availability.Missing, library.availability.check(id))

        val result = relinker.relink(id, library.pick("Elsewhere/Moved Book"))

        assertEquals(RelinkResult.Relinked(leftOut = emptyList()), result)
        val after = library.details(id)
        assertKept(before, after)
        assertEquals(2.seconds, after.book.position)
        assertEquals(1.5f, after.book.speed)
        assertNotNull(after.book.coverPath)
        assertEquals(documents.grant("Elsewhere/Moved Book"), after.book.source)
        assertEquals(listOf("01 - Opening.mp3", "Disc 2/02 - Middle.mp3"), after.files.map { it.relativePath })
        assertTrue(after.files.none { it.isMissing })
        after.files.forEach { assertEquals("Elsewhere/Moved Book", it.ref.root) }
        assertEquals(Availability.Available, library.availability.availability.value.of(id))
        assertEquals(0, documents.openSources)
    }

    @Test
    fun wholeFolderRenamedInPlaceIsAFullMatch() = rule.testScope.runTest {
        val id = playedSampleBook()
        val before = library.details(id)
        documents.rename("Books/Sample Book", "Books/Sample Book (2019)")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Sample Book (2019)")))
        assertKept(before, library.details(id))
    }

    @Test
    fun aRenamedFileInsideTheFolderIsFoundBySizeAndDuration() = rule.testScope.runTest {
        val id = playedSampleBook()
        documents.rename("Books/Sample Book/01 - Opening.mp3", "Books/Sample Book/01 - Intro.mp3")
        assertEquals(Availability.Missing, library.availability.check(id))

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Sample Book")))

        assertEquals(listOf("01 - Intro.mp3", "Disc 2/02 - Middle.mp3"), library.details(id).files.map { it.relativePath })
        assertEquals(Availability.Available, library.availability.availability.value.of(id))
    }

    @Test
    fun oneFileRenamedAndOneDeletedAsksForConfirmationAndChangesNothing() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        documents.rename("Books/Three", "Books/Three Moved")
        documents.rename("Books/Three Moved/01 - One.mp3", "Books/Three Moved/01 - Uno.mp3")
        documents.delete("Books/Three Moved/02 - Two.mp3")
        val before = rows(id)

        val result = relinker.relink(id, library.pick("Books/Three Moved"))

        assertEquals(RelinkResult.NeedsConfirmation(found = 2, total = 3, missing = listOf("02 - Two.mp3")), result)
        assertEquals(before, rows(id))
    }

    @Test
    fun aConfirmedPartialMatchLeavesTheMissingFileOutAndKeepsTheTimeline() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        library.repository.setPosition(id, 2.5.seconds)
        val before = library.details(id)
        documents.rename("Books/Three", "Books/Three Moved")
        documents.rename("Books/Three Moved/01 - One.mp3", "Books/Three Moved/01 - Uno.mp3")
        documents.delete("Books/Three Moved/02 - Two.mp3")

        val result = relinker.relink(id, library.pick("Books/Three Moved"), confirmMissing = setOf("02 - Two.mp3"))

        assertEquals(RelinkResult.Relinked(leftOut = listOf("02 - Two.mp3")), result)
        val after = library.details(id)
        assertKept(before, after)
        assertEquals(listOf(false, true, false), after.files.map { it.isMissing })
        assertEquals(listOf("01 - Uno.mp3", "02 - Two.mp3", "03 - Three.mp3"), after.files.map { it.relativePath })
        // The left-out file keeps its old reference; the others point into the new folder.
        assertEquals(listOf("Books/Three Moved", "Books/Three", "Books/Three Moved"), after.files.map { it.ref.root })
        assertEquals(Availability.Available, library.availability.availability.value.of(id))
    }

    @Test
    fun aConfirmationOnlyCoversTheFilesItNames() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        documents.rename("Books/Three", "Books/Three Moved")
        documents.delete("Books/Three Moved/02 - Two.mp3")
        documents.delete("Books/Three Moved/03 - Three.mp3")

        val result = relinker.relink(id, library.pick("Books/Three Moved"), confirmMissing = setOf("02 - Two.mp3"))

        assertEquals(RelinkResult.NeedsConfirmation(1, 3, listOf("02 - Two.mp3", "03 - Three.mp3")), result)
        assertFalse(library.details(id).files.any { it.isMissing })
    }

    @Test
    fun cancellingAPartialMatchLeavesTheBookMissing() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        documents.rename("Books/Three", "Books/Three Moved")
        documents.delete("Books/Three Moved/02 - Two.mp3")
        assertIs<RelinkResult.NeedsConfirmation>(relinker.relink(id, library.pick("Books/Three Moved")))

        // Cancelling is not calling again: the book still points at its old folder.
        assertEquals("Books/Three", library.details(id).book.source.root)
        assertEquals(Availability.Missing, library.availability.check(id))
    }

    @Test
    fun aLeftOutFileFoundLaterIsBroughtBack() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        documents.rename("Books/Three", "Books/Partial")
        documents.rename("Books/Partial/02 - Two.mp3", "Aside/02 - Two.mp3")
        relinker.relink(id, library.pick("Books/Partial"), confirmMissing = setOf("02 - Two.mp3"))
        documents.rename("Aside/02 - Two.mp3", "Books/Partial/02 - Two.mp3")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Partial")))
        assertTrue(library.details(id).files.none { it.isMissing })
    }

    @Test
    fun aFileAlreadyLeftOutIsNotAskedAboutAgain() = rule.testScope.runTest {
        addThreePartBook("Books/Three")
        val id = library.import("Books/Three")
        documents.rename("Books/Three", "Books/Partial")
        documents.delete("Books/Partial/02 - Two.mp3")
        relinker.relink(id, library.pick("Books/Partial"), confirmMissing = setOf("02 - Two.mp3"))
        documents.rename("Books/Partial", "Books/Again")

        assertEquals(RelinkResult.Relinked(leftOut = listOf("02 - Two.mp3")), relinker.relink(id, library.pick("Books/Again")))
    }

    @Test
    fun nestedDiscsMovedTogetherAreAFullMatch() = rule.testScope.runTest {
        documents.addFile("Books/Discs/Disc 1/01.mp3", taggedMp3("One", album = "Discs"))
        documents.addFile("Books/Discs/Disc 1/02.mp3", taggedMp3("Two", album = "Discs"))
        documents.addFile("Books/Discs/Disc 2/01.mp3", untaggedMp3())
        val id = library.import("Books/Discs")
        val before = library.details(id)
        documents.rename("Books/Discs", "Archive/Discs")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Archive/Discs")))
        assertKept(before, library.details(id))
        assertEquals(listOf("Disc 1", "Disc 2"), library.details(id).chapters.map { it.title })
    }

    @Test
    fun aRenamedSubFolderIsMatchedByName() = rule.testScope.runTest {
        documents.addFile("Books/Discs/Disc 1/01.mp3", taggedMp3("One", album = "Discs"))
        documents.addFile("Books/Discs/Disc 1/02.mp3", taggedMp3("Two", album = "Discs"))
        documents.addFile("Books/Discs/Disc 2/01.mp3", taggedMp3("Three", album = "Discs"))
        val id = library.import("Books/Discs")
        val before = library.details(id)
        documents.rename("Books/Discs/Disc 2", "Books/Discs/CD 2")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Discs")))
        assertEquals(listOf("Disc 1/01.mp3", "Disc 1/02.mp3", "CD 2/01.mp3"), library.details(id).files.map { it.relativePath })
        // Sections keep the names they were imported with.
        assertKept(before, library.details(id))
    }

    @Test
    fun aMovedSingleFileIsAFullMatch() = rule.testScope.runTest {
        documents.addFile("Books/Single.m4b", fixture("mp4/tags-both.m4b"))
        val id = library.import("Books/Single.m4b")
        library.repository.setPosition(id, 1.seconds)
        val before = library.details(id)
        documents.rename("Books/Single.m4b", "Elsewhere/Single.m4b")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Elsewhere/Single.m4b")))
        val after = library.details(id)
        assertKept(before, after)
        assertEquals(1.seconds, after.book.position)
        assertEquals("Elsewhere/Single.m4b", after.files.single().ref.root)
    }

    @Test
    fun aRenamedSingleFileIsMatchedBySizeAndDuration() = rule.testScope.runTest {
        documents.addFile("Books/Single.m4b", fixture("mp4/tags-both.m4b"))
        val id = library.import("Books/Single.m4b")
        documents.rename("Books/Single.m4b", "Books/Renamed.m4b")

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Renamed.m4b")))
        assertEquals("Renamed.m4b", library.details(id).files.single().relativePath)
    }

    @Test
    fun anotherSingleFileIsNoMatch() = rule.testScope.runTest {
        documents.addFile("Books/Single.m4b", fixture("mp4/tags-both.m4b"))
        documents.addFile("Books/Other.mp3", untaggedMp3())
        val id = library.import("Books/Single.m4b")
        val before = rows(id)

        assertEquals(RelinkResult.NoMatch, relinker.relink(id, library.pick("Books/Other.mp3")))
        assertEquals(before, rows(id))
    }

    @Test
    fun theWrongFolderIsNoMatchAndChangesNothing() = rule.testScope.runTest {
        val id = playedSampleBook()
        documents.rename("Books/Sample Book", "Books/Moved")
        addThreePartBook("Books/Unrelated")
        val before = rows(id)

        assertEquals(RelinkResult.NoMatch, relinker.relink(id, library.pick("Books/Unrelated")))
        assertEquals(before, rows(id))
        assertEquals(Availability.Missing, library.availability.check(id))
    }

    @Test
    fun aFolderForASingleFileBookIsTheWrongKind() = rule.testScope.runTest {
        documents.addFile("Books/Single.m4b", fixture("mp4/tags-both.m4b"))
        val id = library.import("Books/Single.m4b")
        documents.rename("Books/Single.m4b", "Books/Folder/Single.m4b")
        val before = rows(id)

        assertEquals(RelinkResult.WrongKind(needsFolder = false), relinker.relink(id, library.pick("Books/Folder")))
        assertEquals(before, rows(id))
    }

    @Test
    fun aFileForAFolderBookIsTheWrongKind() = rule.testScope.runTest {
        val id = playedSampleBook()
        val before = rows(id)

        val result = relinker.relink(id, library.pick("Books/Sample Book/01 - Opening.mp3"))

        assertEquals(RelinkResult.WrongKind(needsFolder = true), result)
        assertEquals(before, rows(id))
    }

    @Test
    fun sameNamedFilesOfDifferentSizesPickTheRightOne() = rule.testScope.runTest {
        documents.addFile("Books/Book/01.mp3", taggedMp3("One", album = "Book"))
        val id = library.import("Books/Book")
        documents.delete("Books/Book")
        documents.addFile("Moved/Book/A/01.mp3", untaggedMp3())
        documents.addFile("Moved/Book/B/01.mp3", taggedMp3("One", album = "Book"))

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Moved/Book")))
        assertEquals("B/01.mp3", library.details(id).files.single().relativePath)
    }

    @Test
    fun sameNamedFilesThatCantBeToldApartAreNotGuessed() = rule.testScope.runTest {
        documents.addFile("Books/Book/01.mp3", taggedMp3("One", album = "Book"))
        val id = library.import("Books/Book")
        documents.delete("Books/Book")
        documents.addFile("Moved/Book/A/01.mp3", taggedMp3("One", album = "Book"))
        documents.addFile("Moved/Book/B/01.mp3", taggedMp3("One", album = "Book"))

        assertEquals(RelinkResult.NoMatch, relinker.relink(id, library.pick("Moved/Book")))
    }

    @Test
    fun anotherBooksFolderIsRefused() = rule.testScope.runTest {
        val sample = playedSampleBook()
        addThreePartBook("Books/Three")
        val three = library.import("Books/Three")
        documents.rename("Books/Sample Book", "Books/Moved")
        val before = rows(sample)

        assertEquals(RelinkResult.OtherBook(three), relinker.relink(sample, library.pick("Books/Three")))
        assertEquals(before, rows(sample))
    }

    @Test
    fun anotherBooksFolderPickedAgainIsRefusedByItsFingerprint() = rule.testScope.runTest {
        // On iOS every pick is a new bookmark, so the reference differs; the files and name don't.
        val sample = playedSampleBook()
        addThreePartBook("Books/Three")
        val three = library.import("Books/Three")
        val repick = PickedDocument(documents.grant("Books/Three").copy(child = ""), "Three")

        assertEquals(RelinkResult.OtherBook(three), relinker.relink(sample, repick))
    }

    @Test
    fun accessLostIsRestoredByPickingTheSameFolderAgain() = rule.testScope.runTest {
        val id = playedSampleBook()
        val before = rows(id)
        documents.revoke("Books/Sample Book")
        assertEquals(Availability.AccessLost, library.availability.check(id))

        val result = relinker.relink(id, library.pick("Books/Sample Book"))

        assertEquals(RelinkResult.Relinked(emptyList()), result)
        assertEquals(before, rows(id))
        assertEquals(Availability.Available, library.availability.availability.value.of(id))
    }

    @Test
    fun relinkingAnAvailableBookToItsOwnFolderIsANoOp() = rule.testScope.runTest {
        val id = playedSampleBook()
        assertEquals(Availability.Available, library.availability.check(id))
        val before = rows(id)

        assertEquals(RelinkResult.Unchanged, relinker.relink(id, library.pick("Books/Sample Book")))
        assertEquals(RelinkResult.Unchanged, relinker.relink(id, library.pick("Books/Sample Book")))
        assertEquals(before, rows(id))
    }

    @Test
    fun aPickThatIsGoneFails() = rule.testScope.runTest {
        val id = playedSampleBook()
        addThreePartBook("Books/Three")
        val pick = library.pick("Books/Three")
        documents.delete("Books/Three")

        assertEquals(RelinkResult.Failed(FailureReason.SourceMissing, "Books/Three is Missing"), relinker.relink(id, pick))
    }

    @Test
    fun anUnknownBookIsReported() = rule.testScope.runTest {
        addThreePartBook("Books/Three")

        assertEquals(RelinkResult.NoSuchBook, relinker.relink(BookId(42), library.pick("Books/Three")))
    }

    @Test
    fun theNewPlaceIsTheBooksSourceForDuplicates() = rule.testScope.runTest {
        val id = playedSampleBook()
        documents.rename("Books/Sample Book", "Books/Moved")
        relinker.relink(id, library.pick("Books/Moved"))

        assertEquals(ImportResult.AlreadyInLibrary(id), library.importer.import(library.pick("Books/Moved")))
    }

    @Test
    fun aDeletedAndCopiedFolderOnIosIsRelinkedToTheCopy() = rule.testScope.runTest {
        library.close()
        library = LibraryHarness(FakeDocumentAccess(followsRenames = true))
        val id = playedSampleBook()
        // A rename of the picked folder is followed, so the book stays available...
        documents.rename("Books/Sample Book", "Books/Renamed")
        assertEquals(Availability.Available, library.availability.check(id))
        // ...but a copy elsewhere and the original deleted is not.
        addSampleBook(at = "Books/Copy")
        documents.delete("Books/Renamed")
        assertEquals(Availability.Missing, library.availability.check(id))

        assertEquals(RelinkResult.Relinked(emptyList()), relinker.relink(id, library.pick("Books/Copy")))
        assertEquals(Availability.Available, library.availability.availability.value.of(id))
    }

    @Test
    fun relinkingOnlyReadsAndClosesWhatItOpens() = rule.testScope.runTest {
        val id = playedSampleBook()
        documents.rename("Books/Sample Book/01 - Opening.mp3", "Books/Sample Book/01 - Intro.mp3")

        relinker.relink(id, library.pick("Books/Sample Book"))

        assertEquals(0, documents.openSources)
        assertEquals(
            listOf("01 - Intro.mp3", "Disc 2/02 - Middle.mp3", "cover.jpg", "notes.txt"),
            documents.list(documents.grant("Books/Sample Book")).map { it.relativePath },
        )
    }
}
