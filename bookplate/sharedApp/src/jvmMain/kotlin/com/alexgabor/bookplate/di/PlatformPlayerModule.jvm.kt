package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.player.fakePlayerModule
import org.koin.core.module.Module

// The JVM target only runs tests, which play through the clock-driven fake.
actual val platformPlayerModule: Module = fakePlayerModule
