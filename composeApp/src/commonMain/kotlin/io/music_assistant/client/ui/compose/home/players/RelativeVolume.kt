package io.music_assistant.client.ui.compose.home.players

import kotlin.math.roundToInt

// The web frontend drags at half speed, so a full-width swipe moves the volume by 50.
private const val RELATIVE_DRAG_SENSITIVITY = 0.5f

/**
 * The volume after dragging [dx] pixels across a slider [widthPx] wide, from [start] (0..100)
 * at the point the drag was recognised. Whole numbers, like the web frontend's slider.
 */
fun relativeVolume(start: Float, dx: Float, widthPx: Float): Float =
    (start + dx / widthPx * 100f * RELATIVE_DRAG_SENSITIVITY).roundToInt().coerceIn(0, 100).toFloat()
