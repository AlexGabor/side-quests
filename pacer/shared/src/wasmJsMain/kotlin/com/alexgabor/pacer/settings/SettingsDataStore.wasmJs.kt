package com.alexgabor.pacer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import org.koin.core.module.Module
import org.koin.dsl.module

internal val settingsStoreModule: Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            storage = WebLocalStorage(PreferencesSerializer, name = SETTINGS_FILE_NAME),
        )
    }
}
