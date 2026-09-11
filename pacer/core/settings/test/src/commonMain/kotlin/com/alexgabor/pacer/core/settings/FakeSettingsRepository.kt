package com.alexgabor.pacer.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


class FakeSettingsRepository(
    risoEffectsEnabled: Boolean = false,
) : SettingsRepository {

    private val risoEffects = MutableStateFlow(risoEffectsEnabled)

    override val risoEffectsEnabled: Flow<Boolean> = risoEffects

    override suspend fun setRisoEffects(enabled: Boolean) {
        risoEffects.value = enabled
    }
}
