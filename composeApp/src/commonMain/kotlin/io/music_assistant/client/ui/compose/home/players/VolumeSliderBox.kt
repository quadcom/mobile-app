package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.common.LocalVolumePreferences
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wraps a volume [slider] with a tap-to-step gesture: a tap on the left half steps
 * the volume down, a tap on the right half steps it up. Taps on/near the thumb and
 * drags are handed off to the slider so normal dragging still works.
 *
 * In relative mode ([LocalVolumePreferences]) the box owns every gesture instead: a drag
 * adjusts the volume from where it started ([relativeVolume]) and reports it through
 * [onValueChange] / [onValueChangeFinished], the same callbacks the slider is given.
 *
 * The single source of truth for the expanded-player and group-settings volume
 * controls — keep the action mapping (down/up vs. group down/up) at the call site.
 */
@Composable
fun VolumeSliderBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    volume: () -> Float,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    slider: @Composable () -> Unit,
) {
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val thumbHitPx = with(LocalDensity.current) { 24.dp.toPx() }
    val volumeForGesture by rememberUpdatedState(volume())
    val relative = LocalVolumePreferences.current.relative
    val onValueChangeForGesture by rememberUpdatedState(onValueChange)
    val onValueChangeFinishedForGesture by rememberUpdatedState(onValueChangeFinished)
    val gesture = if (enabled) {
        Modifier.pointerInput(relative) {
            awaitEachGesture {
                val widthPx = size.width
                if (widthPx == 0) return@awaitEachGesture
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val thumbCenter = (volumeForGesture / 100f) * widthPx
                // Tap on/near thumb: hand off to the Slider so dragging works normally.
                if (!relative && abs(down.position.x - thumbCenter) <= thumbHitPx) {
                    return@awaitEachGesture
                }
                down.consume()
                var dragged = false
                // Measured from where the drag was recognised, so slop travel is no volume change.
                var anchorX = 0f
                var startVolume = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!dragged &&
                        (change.position - down.position).getDistance() > touchSlopPx
                    ) {
                        dragged = true
                        anchorX = change.position.x
                        startVolume = volumeForGesture
                    }
                    if (relative && dragged && !change.changedToUp()) {
                        onValueChangeForGesture(
                            relativeVolume(startVolume, change.position.x - anchorX, widthPx.toFloat()),
                        )
                    }
                    if (change.changedToUp()) {
                        change.consume()
                        if (relative && dragged) onValueChangeFinishedForGesture()
                        if (!dragged) {
                            if (down.position.x < widthPx / 2f) onStepDown() else onStepUp()
                        }
                        break
                    } else {
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }
    Box(modifier = modifier.then(gesture)) {
        slider()
    }
}

/**
 * A volume change handler that ticks the device once per whole-number step while the user drags,
 * when haptics are on. Wraps [set]; compares rounded values so a tick is per step, not per pixel.
 */
@Composable
fun rememberVolumeChange(volume: () -> Float, set: (Float) -> Unit): (Float) -> Unit {
    val haptics = LocalVolumePreferences.current.haptics
    val feedback = LocalHapticFeedback.current
    val volumeNow by rememberUpdatedState(volume)
    val setNow by rememberUpdatedState(set)
    return remember(haptics, feedback) {
        { value: Float ->
            if (haptics && value.roundToInt() != volumeNow().roundToInt()) {
                feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            setNow(value)
        }
    }
}
