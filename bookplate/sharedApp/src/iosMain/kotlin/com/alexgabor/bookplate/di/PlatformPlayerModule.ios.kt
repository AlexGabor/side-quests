package com.alexgabor.bookplate.di

import com.alexgabor.bookplate.core.player.fakePlayerModule
import org.koin.core.module.Module

// Temporary: no iOS engine until M12, so the clock-driven fake plays. See docs/architecture/known-deviations.md.
actual val platformPlayerModule: Module = fakePlayerModule
