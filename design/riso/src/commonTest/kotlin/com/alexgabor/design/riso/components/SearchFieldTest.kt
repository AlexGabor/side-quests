package com.alexgabor.design.riso.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.alexgabor.design.riso.RisoTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchFieldTest {

    @Test
    fun typingReportsTheNewValue() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search")
            }
        }
        onNode(hasSetTextAction()).performTextInput("dune")
        assertEquals("dune", query)
        onNodeWithText("dune").assertExists()
    }

    @Test
    fun placeholderShowsOnlyWhileEmpty() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search")
            }
        }
        onNodeWithText("Search").assertExists()
        query = "dune"
        onNodeWithText("Search").assertDoesNotExist()
    }

    @Test
    fun clearIsOfferedOnlyWhenThereIsText() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = query, onValueChange = { query = it })
            }
        }
        onNodeWithContentDescription("Clear").assertDoesNotExist()

        query = "dune"
        onNodeWithContentDescription("Clear").performClick()
        assertEquals("", query)
        onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun clearCarriesTheCallersDescription() = runComposeUiTest {
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = "dune", onValueChange = {}, clearContentDescription = "Effacer")
            }
        }
        onNodeWithContentDescription("Effacer").assertExists()
    }

    @Test
    fun keyboardSearchReportsTheCurrentValue() = runComposeUiTest {
        var searched: String? = null
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = "dune", onValueChange = {}, onSearch = { searched = it })
            }
        }
        onNode(hasSetTextAction()).performImeAction()
        assertEquals("dune", searched)
    }

    @Test
    fun pastedLineBreaksBecomeSpaces() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            RisoTheme(effectsEnabled = false) {
                SearchField(value = query, onValueChange = { query = it })
            }
        }
        onNode(hasSetTextAction()).performTextInput("dune\nmessiah")
        assertEquals("dune messiah", query)
    }
}
