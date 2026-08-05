package com.example.quranapp.presentation.prayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrayerTimesUiStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun update_concurrentMutationsAreAtomic() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = "initial"))
        val scope = CoroutineScope(dispatcher)

        val j1 = scope.launch { state.update { it.copy(counter = it.counter + 1) } }
        val j2 = scope.launch { state.update { it.copy(counter = it.counter + 1) } }
        j1.join()
        j2.join()

        assertEquals(2, state.first().counter)
        assertEquals("initial", state.first().label)
    }

    @Test
    fun update_100ConcurrentMutationsNoLostWrites() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = "start"))
        val scope = CoroutineScope(dispatcher)

        val jobs = (1..100).map {
            scope.launch { state.update { s -> s.copy(counter = s.counter + 1) } }
        }
        jobs.forEach { it.join() }

        assertEquals(100, state.first().counter)
    }

    @Test
    fun update_preservesUnmodifiedFields() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = "original", isActive = true))

        state.update { it.copy(counter = 42) }

        val s = state.first()
        assertEquals(42, s.counter)
        assertEquals("original", s.label)
        assertEquals(true, s.isActive)
    }

    @Test
    fun update_conditionalMutation() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = "original"))

        state.update { s -> if (s.label == "original") s.copy(label = "modified") else s }

        assertEquals("modified", state.first().label)
    }

    @Test
    fun update_conditionFailsLeavesStateUnchanged() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = "original"))

        state.update { s -> if (s.label == "missing") s.copy(label = "modified") else s }

        assertEquals("original", state.first().label)
    }

    @Test
    fun update_sequentialMutationsCompose() = runTest(dispatcher) {
        val state = MutableStateFlow(TestState(counter = 0, label = ""))

        state.update { it.copy(counter = 1, label = "first") }
        state.update { it.copy(counter = it.counter + 1, label = "second") }
        state.update { it.copy(counter = it.counter + 1, label = "third") }

        val s = state.first()
        assertEquals(3, s.counter)
        assertEquals("third", s.label)
    }

    private data class TestState(
        val counter: Int = 0,
        val label: String = "",
        val isActive: Boolean = false
    )
}
