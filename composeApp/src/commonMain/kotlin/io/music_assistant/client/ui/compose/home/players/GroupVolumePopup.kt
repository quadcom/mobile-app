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
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.inactive
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_group_volume
import org.jetbrains.compose.resources.stringResource

/** One speaker row of the group volume pop-up. [isLeader] rows act on the player itself. */
internal data class GroupVolumeMember(
    val id: String,
    val name: String,
    val volume: Float,
    val isMuted: Boolean?,
    val isLeader: Boolean,
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
    player: PlayerData,
    localPlayerId: String?,
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
    val members = groupVolumeMembers(player, localPlayerId)
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
                    members.forEach { member ->
                        // Stable key: a volume echo from the server never rebuilds a slider mid-drag.
                        key(member.id) {
                            Column {
                                RowName(member.name)
                                VolumeRow(
                                    volume = member.volume,
                                    isMuted = member.isMuted,
                                    enabled = true,
                                    onMuteToggle = {
                                        playerAction(
                                            member.id,
                                            PlayerAction.ToggleMute(isMutedNow = member.isMuted == true),
                                        )
                                    },
                                    onVolumeSet = { playerAction(member.id, PlayerAction.VolumeSet(it.toDouble())) },
                                    onStepDown = { playerAction(member.id, PlayerAction.VolumeDown) },
                                    onStepUp = { playerAction(member.id, PlayerAction.VolumeUp) },
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
                            RowName(stringResource(Res.string.players_group_volume, player.player.name))
                            VolumeRow(
                                volume = player.player.groupVolume,
                                isMuted = player.player.groupVolumeMuted,
                                enabled = player.player.groupVolume != null,
                                onMuteToggle = {
                                    playerAction(
                                        player.player.id,
                                        PlayerAction.GroupToggleMute(isMutedNow = player.player.groupVolumeMuted),
                                    )
                                },
                                onVolumeSet = {
                                    playerAction(player.player.id, PlayerAction.GroupVolumeSet(it.toDouble()))
                                },
                                onStepDown = { playerAction(player.player.id, PlayerAction.GroupVolumeDown) },
                                onStepUp = { playerAction(player.player.id, PlayerAction.GroupVolumeUp) },
                                thumbColor = tint,
                                activeTrackColor = tint,
                                inactiveTrackColor = inactiveTint,
                                muteIconTint = tint,
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
