// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.ui.compose.home.players.PlaybackDelayButtons
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.speakers_control_phone
import musicassistantclient.composeapp.generated.resources.speakers_phone_timing_hint
import musicassistantclient.composeapp.generated.resources.speakers_phone_timing_title
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's own panel, opened from its card. Volume means nothing for the phone's speaker, so
 * the old group sheet gave it timing buttons instead; they live here now, because the phone's card
 * is the one place in the speaker list that names it. [onControl] is null when the phone cannot be
 * picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneTimingSheet(
    onAdjustPlaybackDelay: (Int) -> Unit,
    onControl: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        // A transparent container gives no content colour of its own (it fell back to black).
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sheetBackdrop(
                    LocalSheetHazeState.current,
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SHEET_TINT_ALPHA),
                )
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.speakers_phone_timing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.speakers_phone_timing_hint),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlaybackDelayButtons(isEnabled = true, onAdjust = onAdjustPlaybackDelay)
            onControl?.let { control ->
                FilledTonalButton(
                    onClick = control,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(Res.string.speakers_control_phone)) }
            }
        }
    }
}
