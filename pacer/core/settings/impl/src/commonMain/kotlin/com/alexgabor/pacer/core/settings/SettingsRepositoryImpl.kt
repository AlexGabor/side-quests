package com.alexgabor.pacer.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


private val RisoEffectsEnabledKey = booleanPreferencesKey("riso_effects_enabled")

internal class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val risoEffectsEnabled: Flow<Boolean>
        get() = dataStore.data
            .map { preferences -> preferences[RisoEffectsEnabledKey] ?: RisoEffectsEnabledDefault }
            .flowOn(CoroutineDispatchers.IO)

    override suspend fun setRisoEffects(enabled: Boolean) {
        withContext(CoroutineDispatchers.IO) {
            dataStore.edit { preferences -> preferences[RisoEffectsEnabledKey] = enabled }
        }
    }
}
