package io.music_assistant.client.data.model.client

/**
 * The players the swipe shows: each one the app can act on, except a room grouped under a leader
 * that is itself shown, because the leader's page stands for the whole group (Adrian, 2026-11-04:
 * show only the parents). A member whose leader cannot be shown keeps its own page.
 */
fun List<PlayerData>.pagePlayers(): List<PlayerData> {
    val selectable = filter { it.player.isSelectable }
    val selectableIds = selectable.mapTo(HashSet()) { it.playerId }
    return selectable.filter { it.parentBind?.id !in selectableIds }
}

/** Each grouped room [pagePlayers] leaves out, mapped to its leader, so picking it shows its group. */
fun List<PlayerData>.pageLeaders(): Map<String, String> {
    val pageIds = pagePlayers().mapTo(HashSet()) { it.playerId }
    return mapNotNull { member ->
        member.parentBind?.id?.takeIf { it in pageIds && it != member.playerId }?.let { member.playerId to it }
    }.toMap()
}
