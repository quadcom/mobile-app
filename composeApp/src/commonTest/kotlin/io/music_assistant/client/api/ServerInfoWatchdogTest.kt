package io.music_assistant.client.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerInfoWatchdogTest {
    @Test
    fun firesOnceWhenStuckForTheFullTimeout() = runTest {
        val stuck = MutableStateFlow(false)
        var fired = 0
        val job = launch { stuck.onStuckFor(15_000) { fired++ } }
        runCurrent()

        stuck.value = true
        advanceTimeBy(14_999)
        assertEquals(0, fired)
        advanceTimeBy(2)
        assertEquals(1, fired)
        advanceTimeBy(60_000)
        assertEquals(1, fired)
        job.cancel()
    }

    @Test
    fun leavingTheStateCancelsTheTimer() = runTest {
        val stuck = MutableStateFlow(true)
        var fired = 0
        val job = launch { stuck.onStuckFor(15_000) { fired++ } }
        advanceTimeBy(10_000)

        stuck.value = false
        advanceTimeBy(30_000)
        assertEquals(0, fired)
        job.cancel()
    }

    @Test
    fun reenteringTheStateRestartsTheFullWait() = runTest {
        val stuck = MutableStateFlow(true)
        var fired = 0
        val job = launch { stuck.onStuckFor(15_000) { fired++ } }
        advanceTimeBy(10_000)
        stuck.value = false
        runCurrent()
        stuck.value = true

        advanceTimeBy(14_000)
        assertEquals(0, fired)
        advanceTimeBy(2_000)
        assertEquals(1, fired)
        job.cancel()
    }
}
