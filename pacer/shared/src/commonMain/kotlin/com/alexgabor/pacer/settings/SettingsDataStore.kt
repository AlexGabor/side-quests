package com.alexgabor.pacer.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * The one store Pacer's settings live in, opened where this platform keeps a user's data.
 *
 * Composable because Android is the only target that needs a `Context` to answer, and the
 * composition is the only place one is to be had — which is also what keeps `App()` free of
 * arguments and the four platform entry points free of any of this.
 *
 * Every implementation of this holds its store in a singleton rather than in the [remember] below.
 * DataStore's contract is one instance per file, and a composition is not a long enough life to
 * enforce that: an Android configuration change tears one down and builds another, and the second
 * store would be opened on a file the first one still has.
 */
@Composable
internal expect fun rememberSettingsDataStore(): DataStore<Preferences>

/** [PacerSettingsRepository] over the store this platform keeps them in. */
@Composable
fun rememberPacerSettingsRepository(): PacerSettingsRepository {
    val dataStore = rememberSettingsDataStore()
    return remember(dataStore) { PacerSettingsRepository(dataStore) }
}
