package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The screen behind the speaker sheets, captured with `hazeSource` where the sheets are opened.
 * A local, not a parameter, so both sheets reach it without threading it through every call.
 */
val LocalSheetHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * The speaker sheets' see-through background: the screen behind, blurred, under a translucent
 * tint. The sheets live in their own window, so the blur reads the captured sources
 * ([HazeInput.Sources]) rather than the window's own pixels. Without a captured screen it is the
 * tint alone.
 */
internal fun Modifier.sheetBackdrop(hazeState: HazeState?, tint: Color): Modifier =
    (hazeState?.let { hazeBlur(input = HazeInput.Sources(it), style = HazeBlurStyle { blurRadius(20.dp) }) } ?: this)
        .background(tint)

/** How solid the sheets' tint is over the blur (Adrian, 2026-11-03: 72% read too light on the phone). */
internal const val SHEET_TINT_ALPHA = 0.88f
