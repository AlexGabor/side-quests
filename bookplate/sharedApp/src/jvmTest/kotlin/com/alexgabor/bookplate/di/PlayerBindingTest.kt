package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.player.BookPlayer
import com.alexgabor.bookplate.core.player.FakeBookPlayer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform

class PlayerBindingTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun sharedModuleBindsOneFakePlayer() {
        initKoin()
        val koin = KoinPlatform.getKoin()

        val player = koin.get<BookPlayer>()
        assertIs<FakeBookPlayer>(player)
        assertSame(player, koin.get<BookPlayer>())
        assertNull(player.state.value.book)
    }
}
