package com.alexgabor.pacer.core.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val risoEffectsEnabled: Flow<Boolean>
    suspend fun setRisoEffects(enabled: Boolean)
}
