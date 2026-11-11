package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.inactive
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_group_volume
import org.jetbrains.compose.resources.stringResource

/**
 * One speaker row of the group volume pop-up. [isLeader] rows act on the player itself;
 * [isGroupRoom] rows are a pair or permanent group, driven through its group volume.
 */
internal data class GroupVolumeMember(
    val id: String,
    val name: String,
    val volume: Float,
    val isMuted: Boolean?,
    val isLeader: Boolean,
    val isGroupRoom: Boolean = false,
)

/**
 * The speakers that get their own slider: the leader (unless it is a GROUP-type player, which has
 * no volume of its own) and every bound, reachable member. The local phone is left out because its
 * volume has no meaning for the group.
 */
internal fun groupVolumeMembers(player: PlayerData, localPlayerId: String?): List<GroupVolumeMember> {
    val leader = player.player.takeIf { !it.isGroup && it.canSetVolume }
        ?.let { p ->
            p.volumeLevel?.let { level ->
                GroupVolumeMember(
                    id = p.id,
                    name = p.name,
                    volume = level,
                    isMuted = p.volumeMuted.takeIf { p.canMute },
                    isLeader = true,
                )
            }
        }
    val children = player.childrenBinds.mapNotNull { bind ->
        val level = bind.volume ?: return@mapNotNull null
        if (!bind.isBound || !bind.volumeSliderAccessible || bind.id == localPlayerId) return@mapNotNull null
        GroupVolumeMember(bind.id, bind.name, level, bind.isMuted, isLeader = false)
    }
    return (listOfNotNull(leader) + children).sortedBy { it.name.lowercase() }
}

/** The group [player] belongs to in the speaker list, as leader or member; null when it is in none. */
private fun SpeakerList.groupOf(player: PlayerData) =
    groups.firstOrNull { it.leader == player.playerId || player.playerId in it.members }

/**
 * One row per room of the group, as the web shows them. Hidden pair speakers have no player of their
 * own, so rooms come from the speaker list; a stock server has none, so the bound children are used.
 */
internal fun groupVolumeRooms(
    player: PlayerData,
    list: SpeakerList?,
    players: List<PlayerData>,
    localPlayerId: String?,
): List<GroupVolumeMember> {
    if (list == null) return groupVolumeMembers(player, localPlayerId)
    val group = list.groupOf(player) ?: return emptyList()
    return (listOf(group.leader) + group.members).distinct()
        .filter { it != localPlayerId }
        .mapNotNull { id ->
            val p = players.firstOrNull { it.playerId == id }?.player?.takeIf { !it.isPoweredOff }
                ?: return@mapNotNull null
            val name = list.rooms[id]?.name ?: p.name
            val isLeader = id == group.leader
            if (p.isGroup) {
                val level = p.groupVolume ?: return@mapNotNull null
                GroupVolumeMember(id, name, level, p.groupVolumeMuted, isLeader, isGroupRoom = true)
            } else {
                if (!p.canSetVolume) return@mapNotNull null
                val level = p.volumeLevel ?: return@mapNotNull null
                GroupVolumeMember(id, name, level, p.volumeMuted.takeIf { p.canMute }, isLeader)
            }
        }
        .sortedBy { it.name.lowercase() }
}

/** The player whose group volume the bottom slider drives: the group's leader, else [player]. */
internal fun groupVolumeLeader(player: PlayerData, list: SpeakerList?, players: List<PlayerData>): PlayerData =
    list?.groupOf(player)?.let { g -> players.firstOrNull { it.playerId == g.leader } } ?: player

/** Puts the pop-up's bottom edge on the volume bar's bottom edge, so the group row covers the bar. */
private class BarBottomPositionProvider(private val barBottomPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(0, (barBottomPx - popupContentSize.height).coerceAtLeast(0))
}

/**
 * Every speaker's volume above the group volume, opened by a tap on the expanded player's volume
 * bar. [barBottomPx] is the bar's bottom in window coordinates.
 */
@Composable
internal fun GroupVolumePopup(
    rows: List<GroupVolumeMember>,
    leader: PlayerData,
    barBottomPx: Int,
    tint: Color,
    playerAction: (String, PlayerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 60.dp.toPx() }
    val maxHeight = with(density) { barBottomPx.toDp() } - 8.dp
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val positionProvider = remember(barBottomPx) { BarBottomPositionProvider(barBottomPx) }
    // Starts at the bottom, where the group row sits; the value is clamped once the list is measured.
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val inactiveTint = tint.inactive()

    // The content scrolls, so its own drag would swallow a downward pull; the leftover pull at the
    // top of the list reaches the card through nested scrolling instead.
    val pullToDismiss = remember(dismissThresholdPx) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > dismissThresholdPx) {
                        pulled = 0f
                        currentOnDismiss()
                    }
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) pulled = 0f
                return Offset.Zero
            }
        }
    }
    val handleDrag = remember { floatArrayOf(0f) }
    val handleDragState = rememberDraggableState { delta ->
        handleDrag[0] = (handleDrag[0] + delta).coerceAtLeast(0f)
        if (handleDrag[0] > dismissThresholdPx) {
            handleDrag[0] = 0f
            currentOnDismiss()
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .heightIn(max = maxHeight.coerceAtLeast(0.dp)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column {
                // Vertical-only drag: the sliders' horizontal drags never reach it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(handleDragState, Orientation.Vertical)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .nestedScroll(pullToDismiss)
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEach { member ->
                        // Stable key: a volume echo from the server never rebuilds a slider mid-drag.
                        key(member.id) {
                            Column {
                                RowName(member.name)
                                VolumeRow(
                                    volume = member.volume,
                                    isMuted = member.isMuted,
                                    enabled = true,
                                    onMuteToggle = {
                                        val muted = member.isMuted == true
                                        playerAction(
                                            member.id,
                                            if (member.isGroupRoom) {
                                                PlayerAction.GroupToggleMute(isMutedNow = muted)
                                            } else {
                                                PlayerAction.ToggleMute(isMutedNow = muted)
                                            },
                                        )
                                    },
                                    onVolumeSet = {
                                        val level = it.toDouble()
                                        playerAction(
                                            member.id,
                                            if (member.isGroupRoom) {
                                                PlayerAction.GroupVolumeSet(level)
                                            } else {
                                                PlayerAction.VolumeSet(level)
                                            },
                                        )
                                    },
                                    onStepDown = {
                                        val down = if (member.isGroupRoom) {
                                            PlayerAction.GroupVolumeDown
                                        } else {
                                            PlayerAction.VolumeDown
                                        }
                                        playerAction(member.id, down)
                                    },
                                    onStepUp = {
                                        val up = if (member.isGroupRoom) {
                                            PlayerAction.GroupVolumeUp
                                        } else {
                                            PlayerAction.VolumeUp
                                        }
                                        playerAction(member.id, up)
                                    },
                                    thumbColor = tint,
                                    activeTrackColor = tint,
                                    inactiveTrackColor = inactiveTint,
                                    muteIconTint = tint,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    key("group") {
                        Column {
                            RowName(stringResource(Res.string.players_group_volume, leader.player.name))
                            VolumeRow(
                                volume = leader.player.groupVolume,
                                isMuted = leader.player.groupVolumeMuted,
                                enabled = leader.player.groupVolume != null,
                                onMuteToggle = {
                                    playerAction(
                                        leader.player.id,
                                        PlayerAction.GroupToggleMute(isMutedNow = leader.player.groupVolumeMuted),
                                    )
                                },
                                onVolumeSet = {
                                    playerAction(leader.player.id, PlayerAction.GroupVolumeSet(it.toDouble()))
                                },
                                onStepDown = { playerAction(leader.player.id, PlayerAction.GroupVolumeDown) },
                                onStepUp = { playerAction(leader.player.id, PlayerAction.GroupVolumeUp) },
                                thumbColor = tint,
                                activeTrackColor = tint,
                                inactiveTrackColor = inactiveTint,
                                muteIconTint = tint,
                                // The group slider sends to every member, so it sends less often.
                                throttleMs = LiveVolumeSender.groupThrottleMs(
                                    leader.childrenBinds.count { it.isBound }.coerceAtLeast(rows.size),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowName(name: String) {
    Text(
        text = name,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
