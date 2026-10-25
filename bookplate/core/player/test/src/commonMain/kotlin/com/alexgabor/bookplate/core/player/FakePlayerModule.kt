package com.alexgabor.bookplate.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds one [FakeBookPlayer] as itself and as [BookPlayer], over the bound `LibraryRepository`. It plays
 * for as long as the app runs, so its scope is never cancelled.
 */
val fakePlayerModule = module {
    single { FakeBookPlayer(get(), CoroutineScope(SupervisorJob())) } bind BookPlayer::class
}
