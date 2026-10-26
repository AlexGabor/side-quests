package com.alexgabor.lib.documentaccess

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.lib.coroutine.TestScopeRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class FakeDocumentAccessTest {

    private val rule = TestScopeRule()
    private val access = FakeDocumentAccess()

    @BeforeTest
    fun setUp() {
        rule.install()
        access.addFile("Books/Dune/01.mp3", ByteArray(10) { it.toByte() }, lastModified = 1_000)
        access.addFile("Books/Dune/Disc 2/02.mp3", ByteArray(20), lastModified = 2_000)
        access.addFile("Books/Dune/.DS_Store", ByteArray(1))
        access.addFile("Books/Dune/.hidden/03.mp3", ByteArray(1))
        access.addFile("Books/Other/book.m4b", ByteArray(100) { it.toByte() })
    }

    @AfterTest
    fun tearDown() = rule.reset()

    @Test
    fun listingAFolderIsRecursiveAndSkipsHiddenFiles() = rule.testScope.runTest {
        val folder = access.grant("Books/Dune")

        val entries = access.list(folder)

        assertEquals(listOf("01.mp3", "Disc 2/02.mp3"), entries.map { it.relativePath })
        assertEquals(listOf("01.mp3", "02.mp3"), entries.map { it.name })
        assertEquals(listOf(10L, 20L), entries.map { it.size })
        assertEquals(listOf(1_000L, 2_000L), entries.map { it.lastModified })
        entries.forEach { assertEquals(Reachability.Reachable, access.reachability(it.ref)) }
    }

    @Test
    fun listingAFileGivesThatFile() = rule.testScope.runTest {
        val file = access.grant("Books/Other/book.m4b")

        val entries = access.list(file)

        assertEquals(listOf(DocumentEntry(file, "book.m4b", "book.m4b", 100, 0)), entries)
    }

    @Test
    fun listedReferencesSurviveTheirStringForm() = rule.testScope.runTest {
        val entry = access.list(access.grant("Books/Dune")).last()

        val parsed = DocumentRef.parse(entry.ref.asString())!!

        assertEquals(20L, access.open(parsed).use { it.size })
    }

    @Test
    fun readsAreBoundedAndRandomAccess() = rule.testScope.runTest {
        val source = access.open(access.grant("Books/Other/book.m4b"))

        assertContentEquals(byteArrayOf(95, 96, 97, 98, 99), source.read(95, 10))
        assertContentEquals(byteArrayOf(10, 11), source.read(10, 2))
        assertEquals(0, source.read(100, 10).size)
        assertEquals(1, access.openSources)
        source.close()
        assertEquals(0, access.openSources)
    }

    @Test
    fun readClosesTheSource() = rule.testScope.runTest {
        val size = access.read(access.grant("Books/Other/book.m4b")) { it.size }

        assertEquals(100L, size)
        assertEquals(0, access.openSources)
    }

    @Test
    fun deletedAndRenamedFilesAreMissing() = rule.testScope.runTest {
        val (first, second) = access.list(access.grant("Books/Dune"))

        access.delete("Books/Dune/01.mp3")
        access.rename("Books/Dune/Disc 2", "Books/Dune/Disc Two")

        assertEquals(Reachability.Missing, access.reachability(first.ref))
        assertEquals(Reachability.Missing, access.reachability(second.ref))
        assertEquals(listOf("Disc Two/02.mp3"), access.list(access.grant("Books/Dune")).map { it.relativePath })
    }

    @Test
    fun aDeletedFolderIsMissing() = rule.testScope.runTest {
        val folder = access.grant("Books/Dune")

        access.delete("Books/Dune")

        assertEquals(Reachability.Missing, access.reachability(folder))
        val error = assertFailsWith<DocumentUnavailableException> { access.list(folder) }
        assertEquals(Reachability.Missing, error.reachability)
    }

    @Test
    fun revokingLosesAccessRatherThanGoingMissing() = rule.testScope.runTest {
        val folder = access.grant("Books/Dune")
        val file = access.list(folder).first()

        access.revoke("Books/Dune")

        assertEquals(Reachability.AccessLost, access.reachability(folder))
        assertEquals(Reachability.AccessLost, access.reachability(file.ref))
        val error = assertFailsWith<DocumentUnavailableException> { access.open(file.ref) }
        assertEquals(Reachability.AccessLost, error.reachability)
    }

    @Test
    fun referencesThatWereNeverGrantedHaveNoAccess() = rule.testScope.runTest {
        val ref = DocumentRef("Books/Dune", null, isFolder = true)

        assertEquals(Reachability.AccessLost, access.reachability(ref))
    }

    @Test
    fun renamingAPickedFolderLeavesItsReferencesMissingByDefault() = rule.testScope.runTest {
        val folder = access.grant("Books/Dune")
        val file = access.list(folder).first().ref

        access.rename("Books/Dune", "Books/Dune 2")

        assertEquals(Reachability.Missing, access.reachability(folder))
        assertEquals(Reachability.Missing, access.reachability(file))
    }

    @Test
    fun renamingAPickedFolderIsFollowedLikeIos() = rule.testScope.runTest {
        val following = FakeDocumentAccess(followsRenames = true)
        following.addFile("Books/Dune/01.mp3", ByteArray(10) { it.toByte() })
        val folder = following.grant("Books/Dune")
        val file = following.list(folder).single().ref

        following.rename("Books/Dune", "Books/Dune 2")

        assertEquals(Reachability.Reachable, following.reachability(folder))
        assertEquals(Reachability.Reachable, following.reachability(file))
        assertEquals(listOf("01.mp3"), following.list(folder).map { it.relativePath })
        assertContentEquals(ByteArray(10) { it.toByte() }, following.open(file).use { it.read(0, 10) })
    }

    @Test
    fun renamingAFileInsideAFollowedFolderLeavesItMissing() = rule.testScope.runTest {
        val following = FakeDocumentAccess(followsRenames = true)
        following.addFile("Books/Dune/01.mp3", ByteArray(10))
        val folder = following.grant("Books/Dune")
        val file = following.list(folder).single().ref

        following.rename("Books/Dune/01.mp3", "Books/Dune/01 - Renamed.mp3")

        assertEquals(Reachability.Reachable, following.reachability(folder))
        assertEquals(Reachability.Missing, following.reachability(file))
    }

    @Test
    fun aNewPickOfAFollowedFoldersOldPathPointsAtWhatIsThereNow() = rule.testScope.runTest {
        val following = FakeDocumentAccess(followsRenames = true)
        following.addFile("Books/Dune/01.mp3", ByteArray(10))
        following.grant("Books/Dune")
        following.rename("Books/Dune", "Books/Dune 2")
        following.addFile("Books/Dune/new.mp3", ByteArray(5))

        val again = following.grant("Books/Dune")

        assertEquals(listOf("new.mp3"), following.list(again).map { it.relativePath })
    }

    @Test
    fun countsChecksAndThePeakRunningAtOnce() = rule.testScope.runTest {
        val folder = access.grant("Books/Dune")
        access.checkDelay = 100.milliseconds

        (1..3).map { async { access.reachability(folder) } }.awaitAll()

        assertEquals(3, access.reachabilityChecks)
        assertEquals(3, access.peakConcurrentChecks)
    }

    @Test
    fun foldersCannotBeOpened() = rule.testScope.runTest {
        assertFailsWith<IllegalArgumentException> { access.open(access.grant("Books/Dune")) }
    }

    @Test
    fun anEmptyFolderListsNothing() = rule.testScope.runTest {
        access.addFolder("Empty")

        assertEquals(emptyList(), access.list(access.grant("Empty")))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun picksComeFromTheQueueAndGrantAccess() = runComposeUiTest {
        val picked = mutableListOf<PickedDocument?>()
        lateinit var launcher: DocumentPickerLauncher
        access.enqueuePick("Books/Dune")
        access.enqueueCancel()
        access.enqueuePick("Books/Other/book.m4b")

        setContent { launcher = access.rememberLauncher { picked += it } }
        waitForIdle()
        launcher.pickFolder()
        launcher.pickFolder()
        launcher.pickFile(listOf("audio/*"))
        launcher.pickFolder()

        val (folder, cancelled, file, nothingQueued) = picked
        assertEquals(PickedDocument(DocumentRef("Books/Dune", null, isFolder = true), "Dune"), folder)
        assertNull(cancelled)
        assertEquals("book.m4b", assertIs<PickedDocument>(file).name)
        assertNull(nothingQueued)
        assertEquals(listOf("folder", "folder", "file:audio/*", "folder"), access.pickRequests)
    }
}
