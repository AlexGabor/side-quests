package com.alexgabor.bookplate

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.bookplate.debug.DebugPickStore
import com.alexgabor.bookplate.debug.InMemoryDebugPickStore
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.appstateurl.NoAppUrl
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.fakeDocumentAccessModule
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RootNavigationTest {

    @AfterTest
    fun tearDown() = stopKoin()

    private fun ComposeUiTest.launched(documents: FakeDocumentAccess.() -> Unit = {}) {
        // The same AppUrl a phone gets: one that does nothing.
        startKoin {
            modules(
                module {
                    single<AppUrl> { NoAppUrl }
                    single<DebugPickStore> { InMemoryDebugPickStore() }
                },
                fakeDocumentAccessModule,
            )
        }
        KoinPlatform.getKoin().get<FakeDocumentAccess>().documents()

        setContent {
            RisoTheme(effectsEnabled = false) {
                RootNavigation()
            }
        }
        waitForIdle()
    }

    @Test
    fun aLaunchOpensHome() = runComposeUiTest {
        launched()

        onNodeWithText("Bookplate").assertIsDisplayed()
    }

    @Test
    fun playerOpensForABookAndBackReturnsHome() = runComposeUiTest {
        launched()

        onNodeWithText("Open player").performClick()
        waitForIdle()
        onNodeWithText("Player").assertIsDisplayed()
        onNodeWithText("Book $SampleBookId").assertIsDisplayed()

        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithText("Bookplate").assertIsDisplayed()
    }

    // TEMPORARY (M7 debug entry): removed in M9.
    @Test
    fun theDebugEntryListsAPickedFoldersAudioFiles() = runComposeUiTest {
        launched {
            addFile("Books/Dune/01.mp3", cbrMp3(frames = 10))
            addFile("Books/Dune/notes.txt", "not audio".encodeToByteArray())
            enqueuePick("Books/Dune")
        }

        onNodeWithText("Pick folder").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Folder: Dune (Reachable)").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("01.mp3 · 4.1 KB · 0:00").assertExists()
        assertEquals(0, onAllNodesWithText("notes.txt", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun destinationsSurviveASerializationRoundTrip() {
        val destinations = listOf(BookplateDestination.Home, BookplateDestination.Player("book-1"))

        destinations.forEach { destination ->
            val json = Json.encodeToString(BookplateDestination.serializer(), destination)
            assertEquals(destination, Json.decodeFromString(BookplateDestination.serializer(), json))
        }
    }
}

/** MPEG-1 Layer III frames at 128 kb/s, 44.1 kHz: 417 bytes each, silent. */
private fun cbrMp3(frames: Int): ByteArray {
    val frame = ByteArray(417).also {
        it[0] = 0xFF.toByte()
        it[1] = 0xFB.toByte()
        it[2] = 0x90.toByte()
        it[3] = 0x64
    }
    return ByteArray(frame.size * frames) { frame[it % frame.size] }
}
