package com.alexgabor.bookplate

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Requests to show the playing book, as tapping Android's media notification makes. They carry nothing: Player
 * opens on top for whatever book is loaded. Each request is shown once.
 */
class ShowPlaying {

    private val channel = Channel<Unit>(Channel.CONFLATED)

    /** Each request, to the one collector. */
    val requests: Flow<Unit> = channel.receiveAsFlow()

    fun request() {
        channel.trySend(Unit)
    }
}
