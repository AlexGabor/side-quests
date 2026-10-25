package com.alexgabor.bookplate.core.player

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds one [EngineBookPlayer] playing through [PlaybackService], over the bound `LibraryRepository` and
 * `DocumentAccess`. It plays for as long as the app runs, so its scope is never cancelled.
 */
val playerModule = module {
    single {
        EngineBookPlayer(
            repository = get(),
            output = Media3AudioOutput(androidContext()),
            scope = CoroutineScope(SupervisorJob()),
            backgrounded = appBackgrounded(),
        )
    } bind BookPlayer::class
}

/** Emits each time the app's last activity stops: the app went to the background. */
private fun appBackgrounded(): Flow<Unit> = callbackFlow {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            trySend(Unit)
        }
    }
    lifecycle.addObserver(observer)
    awaitClose { lifecycle.removeObserver(observer) }
}.flowOn(CoroutineDispatchers.Main)
