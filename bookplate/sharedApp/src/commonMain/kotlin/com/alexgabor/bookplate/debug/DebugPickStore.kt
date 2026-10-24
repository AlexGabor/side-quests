package com.alexgabor.bookplate.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.documentaccess.DocumentRef
import com.alexgabor.lib.documentaccess.PickedDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module

// TEMPORARY (M7 debug entry): removed with the rest of `debug/` in M9.

/** Where the document access debug section keeps its picks between launches. */
internal interface DebugPickStore {
    val picks: Flow<List<PickedDocument>>
    suspend fun add(pick: PickedDocument)
    suspend fun clear()
}

/** The DataStore file, named so it can't be mistaken for anything that outlives the debug entry. */
internal const val DEBUG_PICKS_FILE = "debug-picks.preferences_pb"

internal expect val debugPickStoreModule: Module

/** One pick per line, the name and the reference's string form separated by a tab. */
internal class DataStoreDebugPickStore(private val store: DataStore<Preferences>) : DebugPickStore {

    override val picks: Flow<List<PickedDocument>> = store.data
        .map { preferences -> decode(preferences[PICKS].orEmpty()) }
        .flowOn(CoroutineDispatchers.IO)

    override suspend fun add(pick: PickedDocument) {
        store.edit { preferences ->
            val picks = decode(preferences[PICKS].orEmpty()).filter { it.ref != pick.ref } + pick
            preferences[PICKS] = picks.joinToString("\n") { "${it.name.replace('\t', ' ')}\t${it.ref.asString()}" }
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(PICKS) }
    }

    private fun decode(value: String): List<PickedDocument> = value.lines().mapNotNull { line ->
        val name = line.substringBefore('\t', missingDelimiterValue = "")
        DocumentRef.parse(line.substringAfter('\t'))?.let { PickedDocument(it, name) }
    }

    private companion object {
        val PICKS = stringPreferencesKey("picks")
    }
}

/** Keeps picks for as long as the process lives: for tests, and the JVM. */
internal class InMemoryDebugPickStore : DebugPickStore {
    private val state = MutableStateFlow(emptyList<PickedDocument>())
    override val picks: Flow<List<PickedDocument>> = state
    override suspend fun add(pick: PickedDocument) {
        state.value = state.value.filter { it.ref != pick.ref } + pick
    }
    override suspend fun clear() {
        state.value = emptyList()
    }
}
