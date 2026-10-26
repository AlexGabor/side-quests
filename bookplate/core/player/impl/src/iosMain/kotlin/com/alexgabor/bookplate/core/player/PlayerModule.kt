package com.alexgabor.bookplate.core.player

import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * Binds one [EngineBookPlayer] playing through AVFoundation, over the bound `LibraryRepository`, `BookAvailability`
 * and `DocumentAccess`, shown and controlled in Now Playing by a [NowPlayingPanel] that draws coverless books in the
 * bound [PanelInkColors]. It plays for as long as the app
 * runs, so its scope is never cancelled.
 */
val playerModule = module {
    single {
        val scope = CoroutineScope(SupervisorJob())
        EngineBookPlayer(
            repository = get(),
            availability = get(),
            output = AvAudioOutput(documentAccess = get()),
            scope = scope,
            backgrounded = appBackgrounded(),
        ).also { NowPlayingPanel(it, inkColors = get()).start(scope) }
    } bind BookPlayer::class
}

/** Emits each time the app enters the background. */
private fun appBackgrounded(): Flow<Unit> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(
        UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { trySend(Unit) }
    awaitClose { center.removeObserver(observer) }
}.flowOn(CoroutineDispatchers.Main)
