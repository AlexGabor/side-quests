package com.alexgabor.pacer.settings

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebOpfsStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

/**
 * The Origin Private File System, which is the only web storage of the three that is both private
 * to the origin and outlives the tab. Named outright because
 * [PreferenceDataStoreFactory.createWithPath] does not reach it: on the web that overload hardcodes
 * session storage, and settings would go with the tab that set them.
 *
 * OPFS wants a secure context — `localhost` counts — and a browser with `createWritable`, which
 * means Chrome, Edge, Firefox 111 and Safari 18 or newer.
 *
 * See [rememberSettingsDataStore]: one store per file, and a composition is too short a life.
 */
private val store: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.create(
        storage = WebOpfsStorage(PreferencesSerializer, name = SETTINGS_FILE_NAME),
    )
}

@Composable
internal actual fun rememberSettingsDataStore(): DataStore<Preferences> = store
