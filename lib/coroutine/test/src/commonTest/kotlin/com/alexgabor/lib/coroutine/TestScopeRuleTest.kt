package com.alexgabor.lib.coroutine

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest

class TestScopeRuleTest {

    private val rule = TestScopeRule()

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    @Test
    fun installReplacesDispatchersWithOnesOnTheSharedScheduler() {
        listOf(CoroutineDispatchers.IO, CoroutineDispatchers.Default, CoroutineDispatchers.Main).forEach {
            assertSame(rule.testScheduler, assertIs<TestDispatcher>(it).scheduler)
        }
    }

    @Test
    fun workOnIoRunsWhenTheSchedulerAdvances() = rule.testScope.runTest {
        var ran = false
        launch(CoroutineDispatchers.IO) { ran = true }
        assertFalse(ran)
        testScheduler.advanceUntilIdle()
        assertTrue(ran)
    }

    @Test
    fun resetRestoresPlatformDispatchers() {
        rule.reset()
        assertSame(Dispatchers.Default, CoroutineDispatchers.Default)
        assertSame(Dispatchers.Main, CoroutineDispatchers.Main)
    }
}
