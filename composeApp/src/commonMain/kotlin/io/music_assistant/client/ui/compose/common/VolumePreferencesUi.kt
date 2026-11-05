package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.UserPreferences
import org.koin.compose.koinInject

/** The user's volume slider preferences, shared with the web frontend. */
data class VolumePreferences(val relative: Boolean = false, val haptics: Boolean = true)

/**
 * Read by every volume slider, so the gate is not threaded through each screen. The defaults
 * (absolute slider, haptics on) apply when nothing provides it.
 */
val LocalVolumePreferences = compositionLocalOf { VolumePreferences() }

/** Exposes [UserPreferences] via [LocalVolumePreferences]. Provide near the app root. */
@Composable
fun ProvideVolumePreferences(content: @Composable () -> Unit) {
    val preferences by koinInject<UserPreferences>().preferences.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalVolumePreferences provides
            VolumePreferences(preferences.relativeVolume, preferences.volumeHapticsEnabled),
        content,
    )
}
