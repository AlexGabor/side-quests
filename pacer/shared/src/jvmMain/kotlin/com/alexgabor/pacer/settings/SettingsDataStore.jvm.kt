package com.alexgabor.pacer.settings

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path
import okio.Path.Companion.toPath

/** See [rememberSettingsDataStore]: one store per file, and a composition is too short a life. */
private val store: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(produceFile = ::settingsPath)
}

@Composable
internal actual fun rememberSettingsDataStore(): DataStore<Preferences> = store

/**
 * Where the desktop this is running on keeps an application's own data.
 *
 * Each of the three has a place for this and none of them is the same place, so the alternative to
 * asking is a dotfile in the home directory on all of them — which is wrong everywhere but Linux,
 * and is the older convention even there.
 */
private fun settingsPath(): Path {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    val directory = when {
        os.contains("mac") -> "$home/Library/Application Support/Pacer"
        os.contains("win") -> "${System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"}\\Pacer"
        else -> "${System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"}/pacer"
    }
    return "$directory/$SETTINGS_FILE_NAME".toPath()
}
