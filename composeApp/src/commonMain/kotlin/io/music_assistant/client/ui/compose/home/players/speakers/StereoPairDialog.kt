package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.data.speakers.SpeakerListSource
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.stereo_pair_create
import musicassistantclient.composeapp.generated.resources.stereo_pair_failed
import musicassistantclient.composeapp.generated.resources.stereo_pair_first
import musicassistantclient.composeapp.generated.resources.stereo_pair_name
import musicassistantclient.composeapp.generated.resources.stereo_pair_new
import musicassistantclient.composeapp.generated.resources.stereo_pair_no_right
import musicassistantclient.composeapp.generated.resources.stereo_pair_pick
import musicassistantclient.composeapp.generated.resources.stereo_pair_play_again
import musicassistantclient.composeapp.generated.resources.stereo_pair_question
import musicassistantclient.composeapp.generated.resources.stereo_pair_second
import musicassistantclient.composeapp.generated.resources.stereo_pair_setup
import musicassistantclient.composeapp.generated.resources.stereo_pair_yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Marks which speaker of a two-speaker group is the left one, the way Sonos does: the first member plays
 * a ding and the user says whether it is the left speaker. The user picks one of the existing [groups] first,
 * or "New pair…", which picks two speakers from [list], names the pair and has the server make the group.
 * [ownPlayerId] is the phone's own player, which is never offered. The dialog closes when the server has
 * saved the pair; a failure stays on screen.
 */
@Composable
internal fun StereoPairDialog(
    groups: List<PermanentGroup>,
    list: SpeakerList,
    ownPlayerId: String?,
    onDismiss: () -> Unit,
) {
    val source = koinInject<SpeakerListSource>()
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<PermanentGroup?>(null) }
    // With no existing group there is nothing to pick from, so the dialog opens on "New pair…".
    var creating by remember { mutableStateOf(groups.isEmpty()) }
    var first by remember { mutableStateOf<String?>(null) }
    var second by remember { mutableStateOf<String?>(null) }
    var pairName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(Res.string.stereo_pair_failed)
    val group = chosen
    // The speaker that dings is always the first member, so "Yes" and "No" are about that one.
    val dinged = group?.members?.firstOrNull()

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

    fun create() {
        val a = first ?: return
        val b = second ?: return
        val name = pairName.trim().ifEmpty { defaultPairName(list, a) }
        scope.launch {
            busy = true
            error = null
            // The new group goes straight into the ding step; the list is not waited for.
            source.createStereoPair(name, listOf(a, b))
                .onSuccess { chosen = PermanentGroup(it, name, listOf(a, b)) }
                .onFailure { showFailure(it) }
            busy = false
        }
    }

    fun ding() {
        dinged?.let { run({ source.identifyStereoSpeaker(it) }, closeOnSuccess = false) }
    }

    fun save(isLeft: Boolean) {
        val left = group?.let { leftSpeakerFor(it.members, dinged.orEmpty(), isLeft) } ?: return
        run({ source.setStereoPair(group.player, left) }, closeOnSuccess = true)
    }

    LaunchedEffect(group?.player) { ding() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stereo_pair_setup)) },
        text = {
            Column {
                if (group == null) {
                    when {
                        !creating -> {
                            Text(stringResource(Res.string.stereo_pair_pick))
                            groups.forEach { candidate ->
                                TextButton(onClick = { chosen = candidate }) { Text(candidate.name) }
                            }
                            TextButton(onClick = { creating = true }) {
                                Text(stringResource(Res.string.stereo_pair_new))
                            }
                        }
                        first == null -> {
                            Text(stringResource(Res.string.stereo_pair_first))
                            firstPairSpeakers(list, ownPlayerId).forEach { id ->
                                TextButton(onClick = {
                                    first = id
                                    pairName = defaultPairName(list, id)
                                }) { Text(pairSpeakerName(list, id)) }
                            }
                        }
                        second == null -> {
                            Text(stringResource(Res.string.stereo_pair_second))
                            secondPairSpeakers(list, first.orEmpty(), ownPlayerId).forEach { id ->
                                TextButton(onClick = { second = id }) { Text(pairSpeakerName(list, id)) }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = pairName,
                                onValueChange = { pairName = it },
                                label = { Text(stringResource(Res.string.stereo_pair_name)) },
                                singleLine = true,
                            )
                            TextButton(onClick = { create() }, enabled = !busy) {
                                Text(stringResource(Res.string.stereo_pair_create))
                            }
                            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else {
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
                }
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
