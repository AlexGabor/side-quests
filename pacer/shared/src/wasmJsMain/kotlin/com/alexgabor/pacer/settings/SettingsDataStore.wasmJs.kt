package com.alexgabor.pacer.settings

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

/**
 * Local storage, which is scoped to the origin and outlives the tab that wrote to it. Named
 * outright because [PreferenceDataStoreFactory.createWithPath] does not reach it: on the web that
 * overload hardcodes session storage, and settings would go with the tab that set them.
 *
 * The Origin Private File System would do as well for durability, but its writes are asynchronous
 * and its coordinator lets a read land mid-write under the same version number, so a value written
 * here never reaches collectors of [DataStore.data] until the page reloads. Local storage writes
 * are synchronous, leaving no gap to read into. Please do not swap this back.
 *
 * See [rememberSettingsDataStore]: one store per file, and a composition is too short a life.
 */
private val store: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.create(
        storage = WebLocalStorage(PreferencesSerializer, name = SETTINGS_FILE_NAME),
    )
}

@Composable
internal actual fun rememberSettingsDataStore(): DataStore<Preferences> = store
