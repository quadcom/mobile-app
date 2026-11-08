// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.data.speakers.SpeakerListSource
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.player_unavailable
import musicassistantclient.composeapp.generated.resources.players_hide_unavailable
import musicassistantclient.composeapp.generated.resources.players_title
import musicassistantclient.composeapp.generated.resources.speakers_other
import musicassistantclient.composeapp.generated.resources.speakers_this_phone
import musicassistantclient.composeapp.generated.resources.stereo_pair_setup
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** How long a group row takes to fade into, out of, or move within the top rows. */
private const val GROUP_ROW_ANIM_MS = 500

/**
 * The speaker list: a row per playing room (plus the active one) on top, then every other room as
 * a square card by floor. The group editor opens on top of it and returns here when dismissed.
 *
 * [topOrder] lives in the caller so the order of the top rows survives closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerListSheet(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    topOrder: List<String>,
    onTopOrderChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onMoveToPlayer: (String) -> Unit,
    onPlayerAction: (String, PlayerAction) -> Unit,
    hideUnavailable: Boolean,
    onToggleHideUnavailable: () -> Unit,
    canLeaveGroup: Boolean,
    onAdjustPlaybackDelay: (Int) -> Unit,
) {
    val listSource = koinInject<SpeakerListSource>()
    val list by listSource.list.collectAsStateWithLifecycle()

    val activeId = selectedPlayer.playerId
    // Group membership comes from the server's list; a stock server sends none, so every room stands alone.
    val bound = remember(list) { list?.bound.orEmpty() }
    // Read on open; the server's change event keeps it current while the sheet is up.
    LaunchedEffect(Unit) { listSource.refresh() }
    val order = remember(players, bound, topOrder, activeId) {
        nextTopOrder(topOrder, playingRoomIds(players, bound), activeId)
            .filter { id -> players.any { it.playerId == id } }
    }
    LaunchedEffect(order) { if (order != topOrder) onTopOrderChange(order) }

    val grantedScopes = LocalGrantedScopes.current
    val stereoGroups = stereoPairCandidates(list, grantedScopes)
    // "New pair…" needs no existing group, only a server list to pick from and the right to configure players.
    val canSetUpPair = list != null && grantedScopes.canConfigurePlayers
    var stereoOpen by remember { mutableStateOf(false) }
    var editorFor by remember { mutableStateOf<String?>(null) }
    var phoneTimingFor by remember { mutableStateOf<String?>(null) }
    // Set when the editor's leader is taken out: its id and the rooms that were in the group.
    var leaving by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }
    // The server picks the new leader a moment later; the editor and the active room follow it.
    // `list` is a key so the effect waits for the server's re-read, which names the leader.
    LaunchedEffect(players, leaving, list) {
        val (oldId, oldMembers) = leaving ?: return@LaunchedEffect
        val next = successorLeader(oldId, oldMembers, players, list?.bound)
        val stillLeads = players.byId(oldId)?.player?.groupMembers.orEmpty().any { it != oldId }
        // Live data drops the old leader about a second before the server's list catches up; until the list
        // stops naming it, "no group remains" is not yet known.
        val listMoved = list?.let { l -> oldMembers.none { l.bound[it] == oldId } } ?: true
        when {
            next != null -> {
                editorFor = next
                if (activeId == oldId) onMoveToPlayer(next)
                leaving = null
            }
            // No group remains, so there is nothing left to edit. With one room left, the server moved the
            // music to it, so the active room follows it there.
            !stillLeads && listMoved -> {
                oldMembers.singleOrNull()?.takeIf { activeId == oldId }?.let(onMoveToPlayer)
                editorFor = null
                leaving = null
            }
        }
    }
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        // A transparent container gives no content colour of its own (it fell back to black).
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.sheetBackdrop(
                LocalSheetHazeState.current,
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = SHEET_TINT_ALPHA),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.players_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                OverflowMenuButton(
                    options = listOf(
                        OverflowMenuOption(
                            title = stringResource(Res.string.players_hide_unavailable),
                            icon = Icons.Default.VisibilityOff,
                            trailingIcon = Icons.Default.Check.takeIf { hideUnavailable },
                            onClick = onToggleHideUnavailable,
                        ),
                    ) + if (canSetUpPair) {
                        listOf(
                            OverflowMenuOption(
                                title = stringResource(Res.string.stereo_pair_setup),
                                onClick = { stereoOpen = true },
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ) { onClick ->
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.cd_more),
                        )
                    }
                }
            }

            val topIds = order.toSet()
            val phone = players.firstOrNull { it.isLocal && it.playerId !in topIds }
            // The phone is lifted out of the server's sections into "This phone".
            val sections = listSections(list, players) { room, server ->
                room.playerId !in topIds && !room.isLocal &&
                    (!hideUnavailable || (server?.available ?: room.player.isSelectable))
            }
            val unavailableText = stringResource(Res.string.player_unavailable)

            val card: @Composable (PlayerData) -> Unit = { room ->
                val groupColour = list?.colourOf(room.playerId)
                val unavailable = !room.player.isSelectable
                RoomCard(
                    player = room,
                    dimmed = groupColour != null || unavailable,
                    tag = groupColour?.word() ?: unavailableText.takeIf { unavailable },
                    groupColour = groupColour,
                    stereo = list?.rooms?.get(room.playerId)?.isStereoPair == true,
                    // The phone's card always opens its timing panel: timing matters most while it is grouped.
                    onClick = when {
                        room.isLocal -> ({ phoneTimingFor = room.playerId })
                        else -> cardTapTarget(room, bound, unavailable)?.let { id -> { onMoveToPlayer(id) } }
                    },
                )
            }

            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { "top:$it" }) { id ->
                    val row = players.byId(id)
                    if (row != null) {
                        val groupColour = list?.ledColour(id)
                        val members = membersOf(row, players, bound).filter { it.playerId !in topIds }
                        // The leader is a card too, first, so every speaker in the group shows.
                        val groupCards = if (groupColour != null) listOf(row) + members else members
                        // Only group rows animate, 0.5 s ease-out (Adrian, 2026-11-05: a big paused group
                        // vanishing was "jarring"); lone rooms swapping in the same place stay still.
                        val rowSpec = tween<Float>(GROUP_ROW_ANIM_MS, easing = EaseOut)
                        val placeSpec = tween<IntOffset>(GROUP_ROW_ANIM_MS, easing = EaseOut)
                        Column(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = if (groupCards.isNotEmpty()) rowSpec else null,
                                    placementSpec = if (groupCards.isNotEmpty()) placeSpec else null,
                                    fadeOutSpec = if (groupCards.isNotEmpty()) rowSpec else null,
                                )
                                .animateContentSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayingRoomRow(
                                item = row,
                                active = id == activeId,
                                onSelect = { onMoveToPlayer(id) },
                                onOpenGroupEditor = { editorFor = id },
                                onPlayPause = { onPlayerAction(id, PlayerAction.TogglePlayPause) },
                                groupColour = groupColour,
                                stereo = list?.rooms?.get(id)?.isStereoPair == true,
                            )
                            if (groupCards.isNotEmpty()) {
                                RoomGrid(Modifier.padding(start = 16.dp)) {
                                    groupCards.forEach { member ->
                                        key(member.playerId) {
                                            RoomCard(
                                                player = member,
                                                tag = groupColour?.word(),
                                                groupColour = groupColour,
                                                stereo = list?.rooms?.get(member.playerId)?.isStereoPair == true,
                                                onClick = if (member.isLocal) ({ phoneTimingFor = member.playerId }) else null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (order.isNotEmpty() && (sections.isNotEmpty() || phone != null)) {
                    item(key = "split") {
                        HorizontalDivider(
                            modifier = Modifier.animateItem().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                floorSectionItems(sections, card = card)
                if (phone != null) {
                    sectionHeading("phone") { stringResource(Res.string.speakers_this_phone) }
                    item(key = "grid:phone") {
                        RoomGrid(Modifier.animateItem().padding(horizontal = 16.dp)) { card(phone) }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    editorFor?.let { leaderId ->
        val leader = players.byId(leaderId)
        if (leader == null) {
            LaunchedEffect(Unit) { editorFor = null }
        } else {
            GroupEditorSheet(
                leader = leader,
                players = players,
                list = list,
                onPlayerAction = { id, action ->
                    if (action is PlayerAction.LeaveGroup) {
                        leaving = id to bound.filterValues { it == id }.keys
                    }
                    onPlayerAction(id, action)
                },
                canLeaveGroup = canLeaveGroup,
                onDismissRequest = { editorFor = null },
            )
        }
    }

    if (stereoOpen) {
        list?.let {
            StereoPairDialog(
                groups = stereoGroups,
                list = it,
                ownPlayerId = players.firstOrNull { p -> p.isLocal }?.playerId,
                onDismiss = { stereoOpen = false },
            )
        }
    }

    phoneTimingFor?.let { phoneId ->
        val phone = players.byId(phoneId)
        PhoneTimingSheet(
            onAdjustPlaybackDelay = onAdjustPlaybackDelay,
            onControl = phone?.takeIf { it.player.isSelectable }?.let {
                {
                    phoneTimingFor = null
                    onMoveToPlayer(phoneId)
                }
            },
            onDismissRequest = { phoneTimingFor = null },
        )
    }
}

/**
 * A floor heading: 14sp Medium primary. [title] is read in composition so it can be a resource.
 * [action], when given, sits at the right end of the same row.
 */
internal fun LazyListScope.sectionHeading(
    key: String,
    action: (@Composable () -> Unit)? = null,
    title: @Composable () -> String,
) {
    item(key = "head:$key") {
        val text: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                text = title(),
                modifier = modifier,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (action == null) {
            text(Modifier.animateItem().padding(start = 20.dp, end = 16.dp, top = 4.dp))
        } else {
            Row(
                modifier = Modifier.animateItem().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                text(Modifier.weight(1f))
                action()
            }
        }
    }
}

/** The floor sections shared by the list and the editor; the heading-less block has no title. */
internal fun LazyListScope.floorSectionItems(
    sections: List<FloorSection>,
    // Before [card], so callers keep passing the card as the trailing lambda.
    headingAction: (@Composable (FloorSection) -> Unit)? = null,
    card: @Composable (PlayerData) -> Unit,
) {
    sections.forEach { section ->
        val title = section.title
        val action = headingAction?.let { act -> @Composable { act(section) } }
        // A null title is "Other" for that section and no heading for the plain block.
        if (title != null) {
            sectionHeading(section.key, action) { title }
        } else if (section.isOther) {
            sectionHeading(section.key, action) { stringResource(Res.string.speakers_other) }
        }
        item(key = "grid:${section.key}") {
            RoomGrid(Modifier.animateItem().animateContentSize().padding(horizontal = 16.dp)) {
                section.rooms.forEach { room ->
                    key(room.playerId) { card(room) }
                }
            }
        }
    }
}
