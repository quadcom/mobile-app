package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.speakers.HaAreas
import io.music_assistant.client.data.speakers.SpeakerListSource
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.room_picker_add
import musicassistantclient.composeapp.generated.resources.room_picker_failed
import musicassistantclient.composeapp.generated.resources.room_picker_floor
import musicassistantclient.composeapp.generated.resources.room_picker_name
import musicassistantclient.composeapp.generated.resources.room_picker_new_floor
import musicassistantclient.composeapp.generated.resources.room_picker_none
import musicassistantclient.composeapp.generated.resources.room_picker_other
import musicassistantclient.composeapp.generated.resources.room_picker_save
import musicassistantclient.composeapp.generated.resources.room_picker_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Sets the room (Home Assistant area) of one player: "None", rooms under their floors, or a new room.
 * The list is read from the server on every open, so a room renamed in Home Assistant shows at once. The
 * dialog closes when the server has saved the choice; a failure stays on screen with the server's own words.
 */
@Composable
internal fun RoomPickerDialog(
    playerId: String,
    onDismiss: () -> Unit,
) {
    val source = koinInject<SpeakerListSource>()
    val scope = rememberCoroutineScope()
    var areas by remember { mutableStateOf<HaAreas?>(null) }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var floorId by remember { mutableStateOf<String?>(null) }
    var newFloor by remember { mutableStateOf(false) }
    var newFloorName by remember { mutableStateOf("") }
    val failedText = stringResource(Res.string.room_picker_failed)

    fun showFailure(cause: Throwable) {
        error = cause.message?.takeIf { it.isNotBlank() } ?: failedText
    }

    LaunchedEffect(Unit) {
        source.areas().onSuccess { areas = it }.onFailure { showFailure(it) }
    }

    fun choose(areaId: String?) {
        scope.launch {
            busy = true
            error = null
            source.setPlayerArea(playerId, areaId)
                .onSuccess { onDismiss() }
                .onFailure { showFailure(it) }
            busy = false
        }
    }

    fun save() {
        scope.launch {
            busy = true
            error = null
            source.createArea(name.trim(), floorId.takeIf { !newFloor }, newFloorName.trim().takeIf { newFloor })
                .onSuccess { areaId ->
                    source.setPlayerArea(playerId, areaId)
                        .onSuccess { onDismiss() }
                        .onFailure { showFailure(it) }
                }
                .onFailure { showFailure(it) }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.room_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val loaded = areas
                when {
                    loaded == null -> if (error == null) CircularProgressIndicator(Modifier.size(24.dp))
                    adding -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(Res.string.room_picker_name)) },
                            singleLine = true,
                        )
                        Text(
                            text = stringResource(Res.string.room_picker_floor),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        loaded.floors.forEach { floor ->
                            ChoiceRow(floor.name, selected = !newFloor && floorId == floor.floorId, radio = true) {
                                floorId = floor.floorId
                                newFloor = false
                            }
                        }
                        ChoiceRow(stringResource(Res.string.room_picker_new_floor), selected = newFloor, radio = true) {
                            newFloor = true
                            floorId = null
                        }
                        if (newFloor) {
                            OutlinedTextField(
                                value = newFloorName,
                                onValueChange = { newFloorName = it },
                                singleLine = true,
                            )
                        }
                    }
                    else -> {
                        val current = loaded.playerAreas[playerId]
                        ChoiceRow(stringResource(Res.string.room_picker_none), selected = current == null) {
                            choose(null)
                        }
                        loaded.floors.forEach { floor ->
                            val rooms = loaded.areas.filter { it.floorId == floor.floorId }
                            if (rooms.isNotEmpty()) Heading(floor.name)
                            rooms.forEach { room ->
                                ChoiceRow(room.name, selected = room.areaId == current) { choose(room.areaId) }
                            }
                        }
                        val floorIds = loaded.floors.map { it.floorId }.toSet()
                        val others = loaded.areas.filter { it.floorId !in floorIds }
                        if (others.isNotEmpty()) Heading(stringResource(Res.string.room_picker_other))
                        others.forEach { room ->
                            ChoiceRow(room.name, selected = room.areaId == current) { choose(room.areaId) }
                        }
                        TextButton(onClick = { adding = true }, enabled = !busy) {
                            Text(stringResource(Res.string.room_picker_add))
                        }
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(onClick = { save() }, enabled = !busy) {
                    Text(stringResource(Res.string.room_picker_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** A tappable line: a tick when [selected], or a radio button when [radio]. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    radio: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (radio) RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.weight(1f))
        if (selected && !radio) Icon(Icons.Default.Check, contentDescription = null)
    }
}
