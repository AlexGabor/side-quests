package com.alexgabor.pacer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


internal const val SETTINGS_FILE_NAME = "pacer.preferences_pb"

private val RisoEffectsEnabledKey = booleanPreferencesKey("riso_effects_enabled")

interface SettingsRepository {
    val risoEffectsEnabled: Flow<Boolean>
    suspend fun setRisoEffects(enabled: Boolean)
}

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val risoEffectsEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[RisoEffectsEnabledKey] ?: true }

    override suspend fun setRisoEffects(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[RisoEffectsEnabledKey] = enabled }
    }
}
