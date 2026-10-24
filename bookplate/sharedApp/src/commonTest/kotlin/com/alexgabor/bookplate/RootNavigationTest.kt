package com.alexgabor.bookplate

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.lib.appstateurl.AppUrl
import com.alexgabor.lib.appstateurl.NoAppUrl
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RootNavigationTest {

    @AfterTest
    fun tearDown() = stopKoin()

    private fun ComposeUiTest.launched() {
        // The same AppUrl a phone gets: one that does nothing.
        startKoin { modules(module { single<AppUrl> { NoAppUrl } }) }

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

    @Test
    fun destinationsSurviveASerializationRoundTrip() {
        val destinations = listOf(BookplateDestination.Home, BookplateDestination.Player("book-1"))

        destinations.forEach { destination ->
            val json = Json.encodeToString(BookplateDestination.serializer(), destination)
            assertEquals(destination, Json.decodeFromString(BookplateDestination.serializer(), json))
        }
    }
}
