package com.alexgabor.pacer.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import okio.Path.Companion.toPath

/** See [rememberSettingsDataStore]: one store per file, and a composition is too short a life. */
private var store: DataStore<Preferences>? = null

/**
 * The app's own storage, under the `datastore/` subdirectory DataStore conventionally uses.
 *
 * The application context rather than the activity's, since the store outlives every activity that
 * asks for it.
 */
@Composable
internal actual fun rememberSettingsDataStore(): DataStore<Preferences> {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        store ?: PreferenceDataStoreFactory.createWithPath {
            File(context.filesDir, "datastore/$SETTINGS_FILE_NAME").absolutePath.toPath()
        }.also { store = it }
    }
}
