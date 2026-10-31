package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.utils.monotonicMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sends the volume while a slider is dragged, at most once per [throttleMs], like the web frontend.
 * A value that arrives inside the window is held and sent when the window ends, so the last
 * position of the finger is never lost.
 */
class LiveVolumeSender(
    private val scope: CoroutineScope,
    private val throttleMs: Long = LIVE_SEND_THROTTLE_MS,
    private val now: () -> Long = ::monotonicMs,
    private val sendNow: (Int) -> Unit,
) {
    private var lastSent: Int? = null
    private var lastSentAt = 0L
    private var pending: Job? = null

    /** A new drag starts fresh, so an outside volume change between drags cannot look redundant. */
    fun start() {
        pending?.cancel()
        pending = null
        lastSent = null
        lastSentAt = 0L
    }

    fun send(value: Float) {
        val level = value.roundToInt()
        pending?.cancel()
        pending = null
        if (level == lastSent) return
        val wait = lastSentAt + throttleMs - now()
        if (lastSent == null || wait <= 0) {
            emit(level)
        } else {
            pending = scope.launch {
                delay(wait)
                emit(level)
            }
        }
    }

    fun finish(value: Float) {
        pending?.cancel()
        pending = null
        val level = value.roundToInt()
        if (level != lastSent) emit(level)
    }

    private fun emit(level: Int) {
        lastSent = level
        lastSentAt = now()
        sendNow(level)
    }

    companion object {
        const val LIVE_SEND_THROTTLE_MS = 100L
        private const val GROUP_THROTTLE_CAP_MS = 500L

        /** One group command fans out to every member and each change is broadcast to all clients. */
        fun groupThrottleMs(memberCount: Int): Long =
            minOf(GROUP_THROTTLE_CAP_MS, LIVE_SEND_THROTTLE_MS * maxOf(1, memberCount))
    }
}
