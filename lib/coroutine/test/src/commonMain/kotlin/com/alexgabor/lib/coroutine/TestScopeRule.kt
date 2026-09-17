package com.alexgabor.lib.coroutine

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Replaces [CoroutineDispatchers] with test dispatchers sharing [testScheduler].
 *
 * Call [install] from `@BeforeTest` and [reset] from `@AfterTest`.
 */
class TestScopeRule {

    val testScheduler: TestCoroutineScheduler = TestCoroutineScheduler()
    val testDispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
    val testScope: TestScope = TestScope(testDispatcher)

    fun install() {
        CoroutineDispatchers.IO = StandardTestDispatcher(testScheduler)
        CoroutineDispatchers.Default = StandardTestDispatcher(testScheduler)
        CoroutineDispatchers.Main = StandardTestDispatcher(testScheduler)
    }

    fun reset() {
        CoroutineDispatchers.reset()
    }
}
