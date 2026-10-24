package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.library.BookImporter
import com.alexgabor.bookplate.core.library.LibraryRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform

class LibraryBindingTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun sharedModuleBindsTheLibrary() = runBlocking {
        initKoin()
        val koin = KoinPlatform.getKoin()

        koin.get<BookImporter>()
        // Opens the (in-memory, on the JVM) Room database through the bundled driver.
        assertEquals(emptyList(), koin.get<LibraryRepository>().books().first())
    }
}
