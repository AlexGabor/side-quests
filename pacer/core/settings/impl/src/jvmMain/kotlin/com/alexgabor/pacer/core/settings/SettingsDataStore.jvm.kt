package com.alexgabor.pacer.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val settingsStoreModule: Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(produceFile = ::settingsPath)
    }
}

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
