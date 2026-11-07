// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.speakers_add_all
import musicassistantclient.composeapp.generated.resources.speakers_group_hint
import musicassistantclient.composeapp.generated.resources.speakers_group_with
import musicassistantclient.composeapp.generated.resources.speakers_playing
import musicassistantclient.composeapp.generated.resources.speakers_remove_all
import musicassistantclient.composeapp.generated.resources.speakers_this_room
import org.jetbrains.compose.resources.stringResource

/**
 * Full-height editor for the group led by [leader]. A tap on a card adds or removes that room at
 * once and the sheet stays open, so several rooms can be picked. Swiping it down only closes this
 * sheet; the speaker list underneath stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditorSheet(
    leader: PlayerData,
    players: List<PlayerData>,
    list: SpeakerList?,
    onPlayerAction: (String, PlayerAction) -> Unit,
    canLeaveGroup: Boolean,
    onDismissRequest: () -> Unit,
) {
    val bound = remember(list) { list?.bound.orEmpty() }
    // The server's `group_with` for the leader (a stock server sends none, so the leader's own list stands in),
    // plus the leader and its current members so they show highlighted. Never the phone, and an unreachable
    // room is offered only to take it out.
    val sections = remember(list, leader, players, bound) {
        val offeredIds = (list?.rooms?.get(leader.playerId)?.groupWith ?: leader.childrenBinds.map { it.id })
            .toSet() + leader.playerId + bound.filterValues { it == leader.playerId }.keys
        listSections(list, players) { room, _ ->
            room.playerId in offeredIds && !room.isLocal && (
                room.player.isAvailable || room.playerId == leader.playerId ||
                    bound[room.playerId] == leader.playerId
                )
        }
    }
    val thisRoom = stringResource(Res.string.speakers_this_room)
    val playingText = stringResource(Res.string.speakers_playing)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        // A transparent container gives no content colour of its own (it fell back to black).
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .sheetBackdrop(
                    LocalSheetHazeState.current,
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SHEET_TINT_ALPHA),
                ),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = list?.colourOf(leader.playerId)?.word()
                        ?: stringResource(Res.string.speakers_group_with, leader.player.name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.speakers_group_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                floorSectionItems(
                    sections,
                    headingAction = { section ->
                        when (val action = floorGroupAction(section, leader, bound)) {
                            is FloorAction.AddAll -> TextButton(onClick = {
                                onPlayerAction(
                                    leader.playerId,
                                    PlayerAction.GroupManage(toAdd = action.ids, viaSpeakerList = list != null),
                                )
                            }) { Text(stringResource(Res.string.speakers_add_all)) }
                            is FloorAction.RemoveAll -> TextButton(onClick = {
                                onPlayerAction(
                                    leader.playerId,
                                    PlayerAction.GroupManage(toRemove = action.ids, viaSpeakerList = list != null),
                                )
                            }) { Text(stringResource(Res.string.speakers_remove_all)) }
                            FloorAction.None -> Unit
                        }
                    },
                ) { room ->
                    val isLeader = room.playerId == leader.playerId
                    val inGroup = isLeader || bound[room.playerId] == leader.playerId
                    // A room in any group shows that group's colour line and word; others keep the old tag.
                    val roomColour = list?.colourOf(room.playerId)
                    val tag = roomColour?.word() ?: when (val t = editorTag(room, leader, players, bound)) {
                        is EditorTag.Named -> t.name
                        EditorTag.Playing -> playingText
                        EditorTag.ThisRoom -> thisRoom
                        EditorTag.None -> null
                    }
                    // A permanent member cannot be taken out.
                    val locked = if (isLeader) {
                        // The leader leaves via `ungroup`, not `set_members`: the server picks the
                        // successor and moves the queue there. Older servers stop the music instead.
                        !(canLeaveGroup && leader.player.canLeaveOwnGroup)
                    } else {
                        inGroup && leader.childrenBinds.firstOrNull { it.id == room.playerId }?.isManageable == false
                    }
                    RoomCard(
                        player = room,
                        highlighted = inGroup,
                        tag = tag,
                        tagAlpha = 0.6f,
                        groupColour = roomColour,
                        onClick = if (locked) {
                            null
                        } else {
                            {
                                onPlayerAction(
                                    leader.playerId,
                                    when {
                                        isLeader -> PlayerAction.LeaveGroup
                                        inGroup -> PlayerAction.GroupManage(
                                            toRemove = listOf(room.playerId),
                                            viaSpeakerList = list != null,
                                        )
                                        else -> PlayerAction.GroupManage(
                                            toAdd = listOf(room.playerId),
                                            viaSpeakerList = list != null,
                                        )
                                    },
                                )
                            }
                        },
                    )
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
