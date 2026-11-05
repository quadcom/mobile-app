package io.music_assistant.client.ui.compose.home.players.speakers

import io.music_assistant.client.data.location.PlayerLocation
import io.music_assistant.client.data.location.SpeakerGrouping
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId

// Pure grouping and sorting rules for the speaker list and the group editor, kept apart from
// Compose so they can be tested without a UI.

/** One floor of the speaker list. [title] is null for "Other" and for the heading-less block. */
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

/** Maps each room that is bound into another room's group to that group's leader id. */
internal fun boundLeaders(players: List<PlayerData>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    players.forEach { p ->
        p.player.groupMembers?.forEach { member ->
            if (member != p.playerId) result[member] = p.playerId
        }
    }
    // A member the leader's list did not name still says whom it follows.
    players.forEach { p ->
        p.player.syncedTo?.takeIf { it != p.playerId }?.let { result.putIfAbsent(p.playerId, it) }
    }
    return result
}

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

/** The name of the room this one is bound into, or null when it is free to select. */
internal fun groupLeaderName(
    room: PlayerData,
    players: List<PlayerData>,
    bound: Map<String, String>,
): String? = bound[room.playerId]?.let { players.byId(it)?.player?.name }

/** A bound room is greyed and ignores taps; adding and removing live in the group editor. */
internal fun cardTapsIgnored(groupLeaderName: String?): Boolean = groupLeaderName != null

/**
 * Groups rooms by floor: highest level first, then the floor with more rooms, then floor name; rooms with no location, area or
 * floor go last under "Other". With no locations at all there are no headings, just one block.
 */
internal fun floorSections(
    rooms: List<PlayerData>,
    locations: Map<String, PlayerLocation>,
): List<FloorSection> {
    if (rooms.isEmpty()) return emptyList()
    if (locations.isEmpty()) {
        return listOf(FloorSection("all", null, false, rooms.sortedWith(byName)))
    }
    fun floorOf(room: PlayerData): String? =
        locations[room.playerId]?.takeIf { it.areaName != null }?.floorName
    val located = rooms.filter { floorOf(it) != null }.groupBy { floorOf(it) }
    // Floors can share a level (a garage beside the main floor), so the floor with more speakers
    // comes first, then the name. A floor without a level sorts after every floor that has one.
    val floors = located.entries.sortedWith(
        compareByDescending<Map.Entry<String?, List<PlayerData>>> { entry ->
            entry.value.firstNotNullOfOrNull { locations[it.playerId]?.floorLevel } ?: Int.MIN_VALUE
        }.thenByDescending { it.value.size }.thenBy { it.key },
    )
    val sections = floors.map { (name, list) ->
        FloorSection("floor:$name", name, false, list.sortedWith(byName))
    }
    val other = rooms.filter { floorOf(it) == null }.sortedWith(byName)
    return if (other.isEmpty()) sections else sections + FloorSection("other", null, true, other)
}

/**
 * Sections for the server's chosen [grouping]. Area and label sections run A-Z; a room with no area
 * (or no label) goes last under "Other", and a room with two labels appears under both.
 */
internal fun speakerSections(
    rooms: List<PlayerData>,
    locations: Map<String, PlayerLocation>,
    grouping: SpeakerGrouping,
): List<FloorSection> {
    if (grouping == SpeakerGrouping.FLOOR) return floorSections(rooms, locations)
    if (rooms.isEmpty()) return emptyList()
    if (grouping == SpeakerGrouping.NONE || locations.isEmpty()) {
        return listOf(FloorSection("all", null, false, rooms.sortedWith(byName)))
    }
    val isLabel = grouping == SpeakerGrouping.LABEL
    fun titlesOf(room: PlayerData): List<String> = locations[room.playerId]?.let {
        if (isLabel) it.labels.distinct() else listOfNotNull(it.areaName)
    }.orEmpty()
    val prefix = if (isLabel) "label" else "area"
    val titled = rooms.flatMap { room -> titlesOf(room).map { it to room } }.groupBy({ it.first }, { it.second })
    val sections = titled.entries.sortedBy { it.key.lowercase() }.map { (title, list) ->
        FloorSection("$prefix:$title", title, false, list.sortedWith(byName))
    }
    val other = rooms.filter { titlesOf(it).isEmpty() }.sortedWith(byName)
    return if (other.isEmpty()) sections else sections + FloorSection("other", null, true, other)
}

/** Rooms the group editor offers: those the leader can group with, never the leader or the phone. */
internal fun offeredRooms(leader: PlayerData, players: List<PlayerData>): List<PlayerData> {
    val ids = leader.childrenBinds.mapTo(mutableSetOf()) { it.id }
    return players.filter { it.playerId in ids && it.playerId != leader.playerId && !it.isLocal }
}

internal fun editorTag(
    room: PlayerData,
    leader: PlayerData,
    players: List<PlayerData>,
): EditorTag {
    if (room.playerId == leader.playerId) return EditorTag.ThisRoom
    val ownerId = boundLeaders(players)[room.playerId]
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
