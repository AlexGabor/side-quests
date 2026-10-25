package com.alexgabor.bookplate

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.feature.home.HomeScreen
import com.alexgabor.bookplate.feature.player.PlayerScreen
import com.alexgabor.design.navigation.RisoNavigation
import com.alexgabor.design.navigation.rememberDeepLinkedBackStack
import com.alexgabor.design.riso.risograph.paper.risoPaper
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
internal sealed interface BookplateDestination : NavKey {

    @Serializable
    data object Home : BookplateDestination

    @Serializable
    data class Player(val bookId: String) : BookplateDestination
}

/**
 * What the back stack calls each screen. Nothing reads it back on a phone, but the back stack is
 * shared with apps that have an address, so it asks.
 */
internal val BookplateDestination.screenName: String
    get() = when (this) {
        BookplateDestination.Home -> HOME
        is BookplateDestination.Player -> PLAYER
    }

/** Only Home can be built from a name: Player needs a book. */
internal fun bookplateDestinationNamed(screen: String): BookplateDestination? = when (screen) {
    HOME -> BookplateDestination.Home
    else -> null
}

private const val HOME = "home"
private const val PLAYER = "player"

@Composable
fun RootNavigation() {
    val nav = rememberDeepLinkedBackStack(
        serializer = BookplateDestination.serializer(),
        appUrl = koinInject(),
        screenName = { it.screenName },
        keyForScreen = ::bookplateDestinationNamed,
    ) {
        listOf(BookplateDestination.Home)
    }

    RisoNavigation(
        nav = nav,
        modifier = Modifier.risoPaper(),
        entryProvider = entryProvider {
            // Fixed content keys, so a screen's saved state is not tied to the book it was opened for.
            entry<BookplateDestination.Home>(clazzContentKey = { HOME }) {
                HomeScreen(
                    onOpenBook = { id -> nav.open(BookplateDestination.Player(id.value.toString())) },
                )
            }
            entry<BookplateDestination.Player>(clazzContentKey = { PLAYER }) { key ->
                PlayerScreen(
                    bookId = BookId(key.bookId.toLong()),
                    onBackClick = { nav.back() },
                )
            }
        },
    )
}
