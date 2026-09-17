package com.alexgabor.pacer.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import com.alexgabor.lib.coroutine.TestScopeRule
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

class SettingsRepositoryImplTest {

    private val rule = TestScopeRule()
    private val dataStore = FakePreferencesDataStore()

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    @Test
    fun readsDefaultThenStoredValue() = rule.testScope.runTest {
        val repository = SettingsRepositoryImpl(dataStore)

        assertEquals(RisoEffectsEnabledDefault, repository.risoEffectsEnabled.first())

        repository.setRisoEffects(!RisoEffectsEnabledDefault)
        assertEquals(!RisoEffectsEnabledDefault, repository.risoEffectsEnabled.first())
    }

    @Test
    fun dataStoreIsAccessedOnIo() = rule.testScope.runTest {
        val repository = SettingsRepositoryImpl(dataStore)

        repository.risoEffectsEnabled.first()
        assertSame(CoroutineDispatchers.IO, dataStore.readContext?.get(ContinuationInterceptor))

        repository.setRisoEffects(true)
        assertSame(CoroutineDispatchers.IO, dataStore.updateContext?.get(ContinuationInterceptor))
    }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    var readContext: CoroutineContext? = null
    var updateContext: CoroutineContext? = null

    override val data: Flow<Preferences> = flow {
        readContext = currentCoroutineContext()
        emitAll(state)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        updateContext = currentCoroutineContext()
        return transform(state.value).also { state.value = it }
    }
}
