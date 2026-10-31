package io.music_assistant.client.ui.compose.home.players

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LiveVolumeSenderTest {
    private val sent = mutableListOf<Int>()

    private fun TestScope.sender(throttleMs: Long = 100) =
        LiveVolumeSender(this, throttleMs, now = { currentTime }) { sent += it }

    @Test
    fun firstSendIsImmediate() = runTest {
        val s = sender()
        s.send(10f)
        assertEquals(listOf(10), sent)
    }

    @Test
    fun rapidChangesGiveOneTrailingSendWithLatestValue() = runTest {
        val s = sender()
        s.send(10f)
        advanceTimeBy(20); s.send(20f)
        advanceTimeBy(20); s.send(30f)
        advanceTimeBy(20); s.send(40f)
        assertEquals(listOf(10), sent)
        advanceTimeBy(40); runCurrent()
        assertEquals(listOf(10, 40), sent)
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(10, 40), sent)
    }

    @Test
    fun sendAfterWindowIsImmediate() = runTest {
        val s = sender()
        s.send(10f)
        advanceTimeBy(100)
        s.send(20f)
        assertEquals(listOf(10, 20), sent)
    }

    @Test
    fun valuesAreRounded() = runTest {
        val s = sender()
        s.send(10.6f)
        assertEquals(listOf(11), sent)
    }

    @Test
    fun sameValueAsLastSentSendsNothingAndCancelsPending() = runTest {
        val s = sender()
        s.send(10f)
        advanceTimeBy(20); s.send(20f)
        advanceTimeBy(20); s.send(10f)
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(10), sent)
    }

    @Test
    fun finishCancelsPendingAndSkipsDuplicate() = runTest {
        val s = sender()
        s.send(10f)
        advanceTimeBy(20); s.send(20f)
        s.finish(10f)
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(10), sent)
    }

    @Test
    fun finishSendsNewValue() = runTest {
        val s = sender()
        s.send(10f)
        advanceTimeBy(20); s.send(20f)
        s.finish(30f)
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(10, 30), sent)
    }

    @Test
    fun startResetsSoNextSendIsImmediate() = runTest {
        val s = sender()
        s.send(10f)
        s.finish(10f)
        s.start()
        s.send(10f)
        assertEquals(listOf(10, 10), sent)
    }

    @Test
    fun groupThrottleScalesWithMembersAndCapsAtHalfASecond() {
        assertEquals(100L, LiveVolumeSender.groupThrottleMs(0))
        assertEquals(100L, LiveVolumeSender.groupThrottleMs(1))
        assertEquals(300L, LiveVolumeSender.groupThrottleMs(3))
        assertEquals(500L, LiveVolumeSender.groupThrottleMs(5))
        assertEquals(500L, LiveVolumeSender.groupThrottleMs(12))
    }
}
