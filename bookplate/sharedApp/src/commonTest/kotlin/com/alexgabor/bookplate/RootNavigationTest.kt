package com.alexgabor.bookplate

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.FakeLibraryRepository
import com.alexgabor.bookplate.core.library.fakeBook
import com.alexgabor.bookplate.core.library.fakeLibraryModule
import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.fakePlayerModule
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.appstateurl.NoAppUrl
import com.alexgabor.lib.documentaccess.fakeDocumentAccessModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class RootNavigationTest {

    @AfterTest
    fun tearDown() = stopKoin()

    private fun ComposeUiTest.launched(
        showPlaying: Flow<Unit> = emptyFlow(),
        library: FakeLibraryRepository.() -> Unit = {},
    ) {
        // The same AppUrl a phone gets: one that does nothing.
        startKoin {
            modules(
                module {
                    single<AppUrl> { NoAppUrl }
                },
                fakeDocumentAccessModule,
                fakeLibraryModule,
                fakePlayerModule,
            )
        }
        KoinPlatform.getKoin().get<FakeLibraryRepository>().library()

        setContent {
            RisoTheme(effectsEnabled = false) {
                RootNavigation(showPlaying)
            }
        }
        waitForIdle()
    }

    @Test
    fun aLaunchOpensHome() = runComposeUiTest {
        launched()

        onNodeWithText("Search title or author").assertIsDisplayed()
    }

    @Test
    fun playerOpensForABookAndBackReturnsHome() = runComposeUiTest {
        launched { add(fakeBook("Dune", author = "Frank Herbert")) }

        onNodeWithText("Dune").performClick()
        waitForIdle()
        onNodeWithText("Frank Herbert").assertIsDisplayed()
        onNodeWithContentDescription("Chapters").assertIsDisplayed()
        onNodeWithContentDescription("Play").assertIsDisplayed()

        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithText("Search title or author").assertIsDisplayed()
    }

    @Test
    fun miniPlayerOpensPlayerForTheLastPlayedBook() = runComposeUiTest {
        launched {
            add(fakeBook("Dune", author = "Frank Herbert", lastPlayedAt = Instant.fromEpochSeconds(10)))
            add(fakeBook("Emma", author = "Jane Austen"))
        }

        // The row and the mini player both read "Dune"; the mini player is the later one.
        onAllNodesWithText("Dune")[1].performClick()
        waitForIdle()
        onNodeWithText("Frank Herbert").assertIsDisplayed()
        onNodeWithContentDescription("Speed").assertIsDisplayed()
    }

    @Test
    fun showingThePlayingBookOpensPlayerOnTopOfHome() = runComposeUiTest {
        val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var dune: BookId? = null
        launched(requests) {
            dune = add(fakeBook("Dune", author = "Frank Herbert"))
            add(fakeBook("Emma", author = "Jane Austen"))
        }
        KoinPlatform.getKoin().get<BookPlayer>().load(dune!!)
        waitForIdle()

        requests.tryEmit(Unit)
        waitForIdle()
        onNodeWithText("Frank Herbert").assertIsDisplayed()

        // Already showing it: nothing is added, so one back returns Home.
        requests.tryEmit(Unit)
        waitForIdle()
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithText("Search title or author").assertIsDisplayed()
    }

    @Test
    fun showingThePlayingBookWithNothingLoadedStaysHome() = runComposeUiTest {
        val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        launched(requests) { add(fakeBook("Dune", author = "Frank Herbert")) }

        requests.tryEmit(Unit)
        waitForIdle()

        onNodeWithText("Search title or author").assertIsDisplayed()
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
