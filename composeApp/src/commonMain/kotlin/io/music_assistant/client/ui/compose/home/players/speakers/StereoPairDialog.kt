package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.speakers.PermanentGroup
import io.music_assistant.client.data.speakers.SpeakerListSource
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.stereo_pair_failed
import musicassistantclient.composeapp.generated.resources.stereo_pair_no_right
import musicassistantclient.composeapp.generated.resources.stereo_pair_play_again
import musicassistantclient.composeapp.generated.resources.stereo_pair_question
import musicassistantclient.composeapp.generated.resources.stereo_pair_setup
import musicassistantclient.composeapp.generated.resources.stereo_pair_yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Marks which speaker of a stereo pair is the left one, the way Sonos does: the first member plays a ding
 * and the user says whether it is the left speaker. The dialog closes when the server has saved the sides;
 * a failure stays on screen.
 */
@Composable
internal fun StereoPairDialog(
    group: PermanentGroup,
    onDismiss: () -> Unit,
) {
    val source = koinInject<SpeakerListSource>()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(Res.string.stereo_pair_failed)
    // The speaker that dings is always the first member, so "Yes" and "No" are about that one.
    val dinged = group.members.firstOrNull()

    fun showFailure(cause: Throwable) {
        error = cause.message?.takeIf { text -> text.isNotBlank() } ?: failedText
    }

    fun run(call: suspend () -> Result<Unit>, closeOnSuccess: Boolean) {
        scope.launch {
            busy = true
            error = null
            call()
                .onSuccess { if (closeOnSuccess) onDismiss() }
                .onFailure { showFailure(it) }
            busy = false
        }
    }

    fun ding() {
        dinged?.let { run({ source.identifyStereoSpeaker(it) }, closeOnSuccess = false) }
    }

    fun save(isLeft: Boolean) {
        val left = leftSpeakerFor(group.members, dinged.orEmpty(), isLeft) ?: return
        run({ source.setStereoPair(group.player, left) }, closeOnSuccess = true)
    }

    LaunchedEffect(group.player) { ding() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stereo_pair_setup)) },
        text = {
            Column {
                Text(stringResource(Res.string.stereo_pair_question))
                TextButton(onClick = { save(isLeft = true) }, enabled = !busy) {
                    Text(stringResource(Res.string.stereo_pair_yes))
                }
                TextButton(onClick = { save(isLeft = false) }, enabled = !busy) {
                    Text(stringResource(Res.string.stereo_pair_no_right))
                }
                TextButton(onClick = { ding() }, enabled = !busy) {
                    Text(stringResource(Res.string.stereo_pair_play_again))
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
