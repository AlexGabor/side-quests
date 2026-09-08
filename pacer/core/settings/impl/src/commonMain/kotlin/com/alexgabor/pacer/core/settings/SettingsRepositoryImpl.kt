package com.alexgabor.pacer.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val RisoEffectsEnabledKey = booleanPreferencesKey("riso_effects_enabled")

internal class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val risoEffectsEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[RisoEffectsEnabledKey] ?: true }

    override suspend fun setRisoEffects(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[RisoEffectsEnabledKey] = enabled }
    }
}
