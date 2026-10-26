package com.alexgabor.bookplate

import com.alexgabor.bookplate.core.library.Availability
import com.alexgabor.bookplate.core.library.BookAvailability
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.BookImporter
import com.alexgabor.bookplate.core.library.ImportResult
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.bookplate.core.library.libraryModule
import com.alexgabor.bookplate.core.player.FakeBookPlayer
import com.alexgabor.bookplate.core.player.fakePlayerModule
import com.alexgabor.bookplate.feature.home.BookRow
import com.alexgabor.bookplate.feature.home.HomeState
import com.alexgabor.bookplate.feature.home.Library
import com.alexgabor.bookplate.feature.player.PlayerScreenState
import com.alexgabor.bookplate.feature.player.PlayerUi
import com.alexgabor.bookplate.feature.player.RelinkStatus
import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentPicker
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.binds
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Relinking from end to end on the JVM: the app's real library (Room, import, availability, relink) over the fake
 * documents, the fake player, and Home's and Player's state holders, as the app wires them. The device scenarios the
 * user can't easily try by hand, run without a device.
 */
class RelinkEndToEndTest {

    private val rule = TestScopeRule()
    private lateinit var koin: Koin
    private lateinit var documents: FakeDocumentAccess

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() {
        koin.close()
        rule.reset()
    }

    private fun start(followsRenames: Boolean = false) {
        documents = FakeDocumentAccess(followsRenames)
        koin = koinApplication {
            modules(
                module { single { documents } binds arrayOf(DocumentAccess::class, DocumentPicker::class) },
                libraryModule,
                fakePlayerModule,
            )
        }.koin
    }

    private val repository get() = koin.get<LibraryRepository>()
    private val player get() = koin.get<FakeBookPlayer>()

    /**
     * Shaped like the sample library's `Sample Book`: `01 - Opening.mp3` (1 s, tagged with the album `Old Album`, so
     * that is the book's title), `Disc 2/02 - Middle.mp3` (2 s) and a cover.
     */
    private fun addSampleBook(at: String = "Audiobooks/Sample Book") {
        documents.addFile("$at/01 - Opening.mp3", fixture("mp3/id3v1-only.mp3"))
        documents.addFile("$at/Disc 2/02 - Middle.mp3", fixture("mp3/cbr-no-header.mp3"))
        documents.addFile("$at/cover.jpg", fixture("mp3/cover.png"))
    }

    private fun pick(path: String) = PickedDocument(documents.grant(path), path.substringAfterLast('/'))

    /** Imports `Sample Book`, then plays it to 0:02 at 1.5× through Player, as the user would. */
    private suspend fun TestScope.playedSampleBook(): BookId {
        addSampleBook()
        val id = assertIs<ImportResult.Success>(koin.get<BookImporter>().import(pick("Audiobooks/Sample Book"))).bookId
        val screen = player(id)
        screen.onSpeedChange(1.5f)
        screen.onScrubEnd(2.seconds)
        runCurrent()
        return id
    }

    private fun TestScope.home() =
        HomeState(backgroundScope, repository, koin.get(), koin.get()).also { runCurrent() }

    private fun TestScope.player(id: BookId) =
        PlayerScreenState(backgroundScope, id, player, repository, koin.get(), koin.get()).also { runCurrent() }

    private val HomeState.row: BookRow get() = (library.value as Library.Books).rows.single()

    private val PlayerScreenState.shown: PlayerUi get() = assertNotNull(ui.value)

    @Test
    fun movedFolderIsUnavailableThenRelinkedAndResumesAtTheSamePosition() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        val before = repository.book(id).first()!!

        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")

        // Home's load finds it missing.
        val home = home()
        assertEquals("Files missing · Tap to relink", home.row.unavailable)
        // Player shows it missing, and nothing plays.
        val screen = player(id)
        assertEquals("Files missing", screen.shown.unavailable?.title)
        player.play()
        runCurrent()
        assertFalse(player.state.value.isPlaying)

        screen.onRelinkPicked(pick("Audiobooks/Moved Book"))
        runCurrent()

        assertEquals(RelinkStatus.Finished("Relinked “Old Album”", isError = false), screen.relink.value)
        assertNull(screen.shown.unavailable)
        assertEquals("0:02", screen.shown.positionLabel)
        assertEquals("1.5×", screen.shown.speedLabel)
        assertNull(home.row.unavailable)
        val after = repository.book(id).first()!!
        assertEquals(before.chapters, after.chapters)
        assertEquals(before.book.copy(source = after.book.source), after.book)

        screen.onPlayPause()
        runCurrent()
        assertTrue(player.state.value.isPlaying)
        assertEquals(2.seconds, player.state.value.position)
    }

    @Test
    fun partialMatchConfirmedLeavesTheMissingFileOutAndPlays() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        documents.delete("Audiobooks/Moved Book/Disc 2")
        val screen = player(id)

        screen.onRelinkPicked(pick("Audiobooks/Moved Book"))
        runCurrent()
        val asked = assertIs<RelinkStatus.NeedsConfirmation>(screen.relink.value)
        assertEquals(setOf("Disc 2/02 - Middle.mp3"), asked.missing)
        assertEquals("Files missing", screen.shown.unavailable?.title)

        screen.onRelinkConfirmed()
        runCurrent()

        assertEquals("Relinked “Old Album”. 1 missing file will be skipped.", screen.relink.value?.message)
        assertNull(screen.shown.unavailable)
        assertEquals(listOf(false, true), repository.book(id).first()!!.files.map { it.isMissing })
        assertEquals(Availability.Available, koin.get<BookAvailability>().check(id))
    }

    @Test
    fun partialMatchCancelledLeavesTheBookMissing() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        documents.delete("Audiobooks/Moved Book/Disc 2")
        val screen = player(id)
        screen.onRelinkPicked(pick("Audiobooks/Moved Book"))
        runCurrent()

        screen.onRelinkCancelled()
        runCurrent()

        assertNull(screen.relink.value)
        assertEquals("Files missing", screen.shown.unavailable?.title)
        assertEquals("Audiobooks/Sample Book", repository.book(id).first()!!.book.source.root)
    }

    @Test
    fun aFileRenamedInsideTheFolderOnIosIsRelinkedToTheSameFolder() = rule.testScope.runTest {
        start(followsRenames = true)
        val id = playedSampleBook()
        // Renaming the picked folder is followed: still available.
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Renamed Book")
        assertNull(home().row.unavailable)
        // Renaming a file inside it is not.
        documents.rename("Audiobooks/Renamed Book/01 - Opening.mp3", "Audiobooks/Renamed Book/01 - Intro.mp3")
        val screen = player(id)
        assertEquals("Files missing", screen.shown.unavailable?.title)

        screen.onRelinkPicked(pick("Audiobooks/Renamed Book"))
        runCurrent()

        assertEquals("Relinked “Old Album”", screen.relink.value?.message)
        assertEquals("0:02", screen.shown.positionLabel)
    }

    @Test
    fun accessLostIsRestoredByRelinkingToTheSameFolder() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        documents.revoke("Audiobooks/Sample Book")
        assertEquals("Access lost · Tap to relink", home().row.unavailable)
        val screen = player(id)
        assertEquals("Access lost", screen.shown.unavailable?.title)

        screen.onRelinkPicked(pick("Audiobooks/Sample Book"))
        runCurrent()

        assertEquals("Relinked “Old Album”", screen.relink.value?.message)
        assertNull(screen.shown.unavailable)
    }

    @Test
    fun filesPutBackAreFoundWithoutARelink() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        val screen = player(id)
        assertNotNull(screen.shown.unavailable)

        documents.rename("Audiobooks/Moved Book", "Audiobooks/Sample Book")
        screen.onCheckAgain()
        runCurrent()

        assertNull(screen.shown.unavailable)
        assertNull(home().row.unavailable)
    }

    @Test
    fun anotherBooksFolderIsRefused() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        documents.addFile("Audiobooks/Single.m4b", fixture("mp4/tags-both.m4b"))
        koin.get<BookImporter>().import(pick("Audiobooks/Single.m4b"))
        addSampleBook(at = "Audiobooks/Other Copy")
        koin.get<BookImporter>().import(pick("Audiobooks/Other Copy"))
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        val screen = player(id)

        screen.onRelinkPicked(pick("Audiobooks/Other Copy"))
        runCurrent()

        // The copy imported under its folder name has the same tags, so it is also titled "Old Album".
        assertEquals(RelinkStatus.Finished("“Other Copy” is already the book “Old Album”", isError = true), screen.relink.value)
        assertEquals("Files missing", screen.shown.unavailable?.title)
    }

    @Test
    fun aFileGoingMissingDuringPlaybackStopsWithFilesMissing() = rule.testScope.runTest {
        start()
        val id = playedSampleBook()
        val screen = player(id)
        screen.onPlayPause()
        runCurrent()
        assertTrue(screen.shown.isPlaying)

        documents.delete("Audiobooks/Sample Book/Disc 2/02 - Middle.mp3")
        player.failPlayback()
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertFalse(player.state.value.isPlaying)
        assertEquals("Files missing", screen.shown.unavailable?.title)
    }

    private fun fixture(path: String): ByteArray =
        File("../../lib/audiometadata/src/jvmTest/resources/fixtures/$path").readBytes()
}
