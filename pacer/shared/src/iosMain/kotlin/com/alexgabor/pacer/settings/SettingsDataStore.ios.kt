package com.alexgabor.pacer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal val settingsStoreModule: Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(produceFile = ::settingsPath)
    }
}

/**
 * Application Support rather than Documents, which is the user's and can be handed to them by the
 * Files app. A setting is the app's own bookkeeping and has no business being visible there.
 *
 * The directory itself need not exist yet — DataStore creates the parents of the file it opens.
 */
private fun settingsPath(): Path {
    val directory = NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
    return "$directory/$SETTINGS_FILE_NAME".toPath()
}
