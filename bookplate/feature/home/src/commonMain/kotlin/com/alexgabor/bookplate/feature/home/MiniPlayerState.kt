package com.alexgabor.bookplate.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.alexgabor.bookplate.core.library.Book
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.bookplate.core.library.LibraryRepository
import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.PlaybackState
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The last-played book as the mini player shows it. [progress] is the fraction played. */
data class MiniPlayerUi(
    val bookId: BookId,
    val title: String,
    val progress: Float,
    val isPlaying: Boolean,
)

/**
 * The mini player: the library's last-played book, live from [player] while the player holds it and as
 * saved in [repository] otherwise.
 */
class MiniPlayerState(
    scope: CoroutineScope,
    repository: LibraryRepository,
    private val player: BookPlayer,
) {

    /** `null` while no book has been played: the mini player is hidden. */
    val mini: State<MiniPlayerUi?>
        field = mutableStateOf<MiniPlayerUi?>(null)

    init {
        scope.launch {
            combine(repository.lastPlayed, player.state, ::miniPlayerOf).collect { mini.value = it }
        }
    }

    /** Plays or pauses the shown book, loading it first when the player holds another one or none. */
    fun onPlayPause() {
        val shown = mini.value ?: return
        val playback = player.state.value
        when {
            playback.book?.id != shown.bookId -> {
                player.load(shown.bookId)
                player.play()
            }
            playback.isPlaying -> player.pause()
            else -> player.play()
        }
    }
}

internal fun miniPlayerOf(lastPlayed: Book?, playback: PlaybackState): MiniPlayerUi? {
    val book = lastPlayed ?: return null
    val live = playback.takeIf { it.book?.id == book.id }
    val position = live?.position ?: book.position
    val duration = live?.duration ?: book.duration
    return MiniPlayerUi(
        bookId = book.id,
        title = book.title,
        progress = if (duration > Duration.ZERO) (position / duration).toFloat().coerceIn(0f, 1f) else 0f,
        isPlaying = live?.isPlaying == true,
    )
}

/** Plain `remember`: the mini player has nothing of its own to save, it reads everything back. */
@Composable
fun rememberMiniPlayerState(
    repository: LibraryRepository = koinInject(),
    player: BookPlayer = koinInject(),
): MiniPlayerState {
    val scope = rememberCoroutineScope()
    return remember(scope, repository, player) { MiniPlayerState(scope, repository, player) }
}
