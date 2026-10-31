package io.music_assistant.client.ui.compose.home.players

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeVolumeTest {
    @Test
    fun `a full-width drag moves the volume by half the range`() {
        assertEquals(70f, relativeVolume(start = 20f, dx = 1000f, widthPx = 1000f))
        assertEquals(20f, relativeVolume(start = 70f, dx = -1000f, widthPx = 1000f))
    }

    @Test
    fun `no travel keeps the volume where the drag began`() {
        assertEquals(42f, relativeVolume(start = 42f, dx = 0f, widthPx = 800f))
    }

    @Test
    fun `the volume stays between 0 and 100`() {
        assertEquals(100f, relativeVolume(start = 90f, dx = 1000f, widthPx = 1000f))
        assertEquals(0f, relativeVolume(start = 10f, dx = -1000f, widthPx = 1000f))
    }

    @Test
    fun `the result is a whole number`() {
        // 30 px of 1000 is 1.5 points; 7 px is 0.35.
        assertEquals(52f, relativeVolume(start = 50f, dx = 30f, widthPx = 1000f))
        assertEquals(50f, relativeVolume(start = 50f, dx = 7f, widthPx = 1000f))
    }
}
