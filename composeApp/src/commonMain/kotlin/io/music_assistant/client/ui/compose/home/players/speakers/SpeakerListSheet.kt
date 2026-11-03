// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.location.PlayerLocationSource
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
) {
    val locationSource = koinInject<PlayerLocationSource>()
    val locations by locationSource.locations.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { locationSource.refresh() }

    val activeId = selectedPlayer.playerId
    val bound = remember(players) { boundLeaders(players) }
    val order = remember(players, topOrder, activeId) {
        nextTopOrder(topOrder, playingRoomIds(players, bound), activeId)
            .filter { id -> players.any { it.playerId == id } }
    }
    LaunchedEffect(order) { if (order != topOrder) onTopOrderChange(order) }

    var editorFor by remember { mutableStateOf<String?>(null) }
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
                    ),
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
            val cardRooms = players
                .filter { it.playerId !in topIds && !it.isLocal }
                .filter { !hideUnavailable || it.player.isSelectable }
            val phone = players.firstOrNull { it.isLocal && it.playerId !in topIds }
            val sections = floorSections(cardRooms, locations)
            val unavailableText = stringResource(Res.string.player_unavailable)

            val card: @Composable (PlayerData) -> Unit = { room ->
                val leaderName = groupLeaderName(room, players, bound)
                val unavailable = !room.player.isSelectable
                val ignored = cardTapsIgnored(leaderName) || unavailable
                RoomCard(
                    player = room,
                    dimmed = leaderName != null || unavailable,
                    tag = leaderName ?: unavailableText.takeIf { unavailable },
                    onClick = if (ignored) null else ({ onMoveToPlayer(room.playerId) }),
                )
            }

            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { "top:$it" }) { id ->
                    val row = players.byId(id)
                    if (row != null) {
                        val members = membersOf(row, players, bound).filter { it.playerId !in topIds }
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .animateContentSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayingRoomRow(
                                item = row,
                                active = id == activeId,
                                onSelect = { onMoveToPlayer(id) },
                                onOpenGroupEditor = { editorFor = id },
                            )
                            if (members.isNotEmpty()) {
                                RoomGrid(Modifier.padding(start = 16.dp)) {
                                    members.forEach { member ->
                                        key(member.playerId) { RoomCard(player = member) }
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
                floorSectionItems(sections, card)
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
                locations = locations,
                onPlayerAction = onPlayerAction,
                onDismissRequest = { editorFor = null },
            )
        }
    }
}

/** A floor heading: 14sp Medium primary. [title] is read in composition so it can be a resource. */
internal fun LazyListScope.sectionHeading(key: String, title: @Composable () -> String) {
    item(key = "head:$key") {
        Text(
            text = title(),
            modifier = Modifier.animateItem().padding(start = 20.dp, end = 16.dp, top = 4.dp),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The floor sections shared by the list and the editor; the heading-less block has no title. */
internal fun LazyListScope.floorSectionItems(
    sections: List<FloorSection>,
    card: @Composable (PlayerData) -> Unit,
) {
    sections.forEach { section ->
        val title = section.title
        if (section.isOther) {
            sectionHeading(section.key) { stringResource(Res.string.speakers_other) }
        } else if (title != null) {
            sectionHeading(section.key) { title }
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
