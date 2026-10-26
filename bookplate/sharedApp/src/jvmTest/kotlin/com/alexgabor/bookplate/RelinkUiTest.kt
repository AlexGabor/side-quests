package com.alexgabor.bookplate

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.bookplate.core.library.BookImporter
import com.alexgabor.bookplate.core.library.ImportResult
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.bookplate.core.library.libraryModule
import com.alexgabor.bookplate.core.library.BookInkPicker
import com.alexgabor.bookplate.core.library.FakeBookInkPicker
import com.alexgabor.bookplate.core.player.fakePlayerModule
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.appstateurl.NoAppUrl
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentPicker
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * The relink flow through the app's own screens, on the JVM: the real library over the fake documents, whose picker
 * returns what the test queues. What a Maestro flow does on a device, without the system picker.
 */
@OptIn(ExperimentalTestApi::class)
class RelinkUiTest {

    private val documents = FakeDocumentAccess()

    @AfterTest
    fun tearDown() = stopKoin()

    private fun ComposeUiTest.launched() {
        startKoin {
            modules(
                module {
                    single<AppUrl> { NoAppUrl }
                    single { ShowPlaying() }
                    single<BookInkPicker> { FakeBookInkPicker() }
                    single { documents } binds arrayOf(DocumentAccess::class, DocumentPicker::class)
                },
                libraryModule,
                fakePlayerModule,
            )
        }
        setContent {
            RisoTheme(effectsEnabled = false) {
                RootNavigation()
            }
        }
        waitForIdle()
    }

    private fun addSampleBook(): Unit = runBlocking {
        documents.addFile("Audiobooks/Sample Book/01 - Opening.mp3", fixture("mp3/id3v1-only.mp3"))
        documents.addFile("Audiobooks/Sample Book/Disc 2/02 - Middle.mp3", fixture("mp3/cbr-no-header.mp3"))
        val koin = KoinPlatform.getKoin()
        val pick = PickedDocument(documents.grant("Audiobooks/Sample Book"), "Sample Book")
        val id = assertIs<ImportResult.Success>(koin.get<BookImporter>().import(pick)).bookId
        koin.get<LibraryRepository>().setPosition(id, 2.seconds)
    }

    private fun ComposeUiTest.openSampleBook() {
        waitForText("Old Album")
        onNodeWithText("Old Album").performClick()
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aMovedFolderIsMarkedOnHomeAndRelinkedFromPlayer() = runComposeUiTest {
        launched()
        addSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        openSampleBook()
        waitForText("Files missing")
        onNodeWithText("Relink").assertIsDisplayed()
        onNodeWithText("Check again").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasContentDescription("Play")).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasContentDescription("Chapters")).fetchSemanticsNodes().size)

        documents.enqueuePick("Audiobooks/Moved Book")
        onNodeWithText("Relink").performClick()

        waitForText("Relinked “Old Album”")
        assertEquals(listOf("folder"), documents.pickRequests)
        onNodeWithContentDescription("Play").assertIsDisplayed()
        onNodeWithText("0:02").assertIsDisplayed()
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(0, onAllNodes(hasText("Relinked “Old Album”")).fetchSemanticsNodes().size)
        runBlocking {
            val book = KoinPlatform.getKoin().get<LibraryRepository>().books().first().single()
            assertEquals("Audiobooks/Moved Book", book.source.root)
        }
    }

    @Test
    fun homeMarksAMissingBookWhenItLoads() = runComposeUiTest {
        launched()
        addSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")

        // Back from Player, Home is created again and checks the library, as at a launch.
        openSampleBook()
        waitForText("Files missing")
        onNodeWithContentDescription("Back").performClick()

        waitForText("Files missing · Tap to relink")
    }

    @Test
    fun aPartialMatchIsConfirmedFromPlayer() = runComposeUiTest {
        launched()
        addSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        documents.delete("Audiobooks/Moved Book/Disc 2")
        openSampleBook()
        waitForText("Files missing")

        documents.enqueuePick("Audiobooks/Moved Book")
        onNodeWithText("Relink").performClick()
        waitForText("Relink anyway")
        onNodeWithText(
            "Found 1 of 2 files in “Moved Book”. Not found: Disc 2/02 - Middle.mp3. " +
                "Parts of the book without their files will be skipped.",
        ).assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Relink anyway").performClick()

        waitForText("Relinked “Old Album”. 1 missing file will be skipped.")
        onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun aCancelledPartialMatchKeepsTheBookMissing() = runComposeUiTest {
        launched()
        addSampleBook()
        documents.rename("Audiobooks/Sample Book", "Audiobooks/Moved Book")
        documents.delete("Audiobooks/Moved Book/Disc 2")
        openSampleBook()
        waitForText("Files missing")
        documents.enqueuePick("Audiobooks/Moved Book")
        onNodeWithText("Relink").performClick()
        waitForText("Relink anyway")

        onNodeWithText("Cancel").performClick()
        waitForText("Relink")

        onNodeWithText("Files missing").assertIsDisplayed()
        assertEquals(0, onAllNodes(hasText("Relink anyway")).fetchSemanticsNodes().size)
    }

    private fun fixture(path: String): ByteArray =
        File("../../lib/audiometadata/src/jvmTest/resources/fixtures/$path").readBytes()
}
