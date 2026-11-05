package io.music_assistant.client.ui.compose.home.players.speakers

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.data.speakers.SectionKind
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.data.speakers.SpeakerRoom

// Pure rules for drawing the server's speaker list and the group editor, kept apart from
// Compose so they can be tested without a UI.

/** One section of the speaker list. [title] is null for "Other" and for the heading-less block. */
internal data class FloorSection(
    val key: String,
    val title: String?,
    val isOther: Boolean,
    val rooms: List<PlayerData>,
)

/** What a card in the group editor says at its top edge. */
internal sealed interface EditorTag {
    data object ThisRoom : EditorTag
    data object Playing : EditorTag
    data class Named(val name: String) : EditorTag
    data object None : EditorTag
}

private val byName = compareBy<PlayerData>({ it.player.name.lowercase() }, { it.playerId })

/**
 * Ids of the rooms that get a row: rooms actually playing (or announcing) and not bound into another
 * room's group. A paused group does not count (Adrian, 2026-11-03: "why is the dining room at the
 * top? Nothing is playing there").
 */
internal fun playingRoomIds(players: List<PlayerData>, bound: Map<String, String>): List<String> =
    players
        .filter { it.playerId !in bound }
        .filter { it.player.isPlaying || it.player.isAnnouncing }
        .map { it.playerId }

/**
 * Append-only order of the top rows. A room keeps its place until it stops playing and is not
 * the active room; new ones go to the end, so switching the active room never reshuffles.
 */
internal fun nextTopOrder(
    previous: List<String>,
    playingIds: List<String>,
    activeId: String,
): List<String> {
    val keep = previous.filter { it in playingIds || it == activeId }
    return keep + (playingIds + activeId).filter { it !in keep }.distinct()
}

/** The members bound to [leader], sorted by name. */
internal fun membersOf(
    leader: PlayerData,
    players: List<PlayerData>,
    bound: Map<String, String>,
): List<PlayerData> =
    players.filter { bound[it.playerId] == leader.playerId }.sortedWith(byName)

/**
 * The player a tap on this card selects: null when the room is unavailable, the group leader's id when
 * the room is bound into a group, else the room's own id. A grouped card stays greyed but a tap opens its
 * group (Adrian, 2026-11-05); adding and removing stay in the group editor.
 */
internal fun cardTapTarget(room: PlayerData, bound: Map<String, String>, unavailable: Boolean): String? =
    if (unavailable) null else bound[room.playerId] ?: room.playerId

/**
 * The sections to draw, in the server's order, with each room joined to its live player data by id. [keep]
 * decides per room from its live data and the server's entry for it (null on a stock server). A server room
 * the app has no live data for is skipped: the cards draw from [PlayerData], so there is nothing to grey out.
 * A stock server (null [list]) gives one plain block sorted by name, with no headings.
 */
internal fun listSections(
    list: SpeakerList?,
    players: List<PlayerData>,
    keep: (PlayerData, SpeakerRoom?) -> Boolean,
): List<FloorSection> {
    if (list == null) {
        val rooms = players.filter { keep(it, null) }.sortedWith(byName)
        return if (rooms.isEmpty()) emptyList() else listOf(FloorSection("all", null, false, rooms))
    }
    val live = players.associateBy { it.playerId }
    return list.sections.mapNotNull { section ->
        val rooms = section.rooms.distinct().mapNotNull { id -> live[id]?.takeIf { keep(it, list.rooms[id]) } }
        val isOther = section.kind == SectionKind.OTHER
        if (rooms.isEmpty()) null else FloorSection(section.key, section.title, isOther, rooms)
    }
}

/**
 * The room that leads the group once [oldLeaderId] has left it: another player whose member list holds
 * one of [oldMembers] besides itself (a room that merely went solo lists only itself). Null while the
 * server has not moved the group yet, or when no group remains.
 */
internal fun successorLeader(
    oldLeaderId: String,
    oldMembers: Set<String>,
    players: List<PlayerData>,
): String? = players.firstOrNull { p ->
    p.playerId != oldLeaderId && p.player.groupMembers.orEmpty().any { it != p.playerId && it in oldMembers }
}?.playerId

internal fun editorTag(
    room: PlayerData,
    leader: PlayerData,
    players: List<PlayerData>,
    bound: Map<String, String>,
): EditorTag {
    if (room.playerId == leader.playerId) return EditorTag.ThisRoom
    val ownerId = bound[room.playerId]
    return when {
        ownerId == leader.playerId -> EditorTag.Named(leader.player.name)
        ownerId == null && room.player.isPlaying -> EditorTag.Playing
        ownerId != null -> players.byId(ownerId)?.let { EditorTag.Named(it.player.name) } ?: EditorTag.None
        else -> EditorTag.None
    }
}

/** What the button on a floor heading in the group editor does for the group led by the leader. */
internal sealed interface FloorAction {
    data class AddAll(val ids: List<String>) : FloorAction
    data class RemoveAll(val ids: List<String>) : FloorAction
    data object None : FloorAction
}

/**
 * Adds the floor's rooms that are not yet in the group; once all are in, offers to remove them.
 * Unavailable rooms are never added, and permanent members are never removed.
 */
internal fun floorGroupAction(
    section: FloorSection,
    leader: PlayerData,
    bound: Map<String, String>,
): FloorAction {
    if (section.isOther) return FloorAction.None
    val inGroup = { room: PlayerData -> bound[room.playerId] == leader.playerId }
    val addable = section.rooms
        .filter { !inGroup(it) && it.player.isAvailable && it.playerId != leader.playerId }
        .map { it.playerId }
        .distinct()
    if (addable.isNotEmpty()) return FloorAction.AddAll(addable)
    val removable = section.rooms
        .filter { inGroup(it) && leader.childrenBinds.firstOrNull { b -> b.id == it.playerId }?.isManageable != false }
        .map { it.playerId }
        .distinct()
    return if (removable.isNotEmpty()) FloorAction.RemoveAll(removable) else FloorAction.None
}
