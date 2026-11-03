package io.music_assistant.client.data.location

import kotlinx.coroutines.flow.StateFlow

/**
 * Where a Music Assistant player sits in the home, as Home Assistant records it on the player's
 * device. Any field is null when Home Assistant has no area, or the area has no floor.
 */
data class PlayerLocation(
    val areaName: String?,
    val floorName: String?,
    /** Home Assistant's floor `level`; higher floors sort first in the speaker list. */
    val floorLevel: Int?,
)

/**
 * Player locations read from Home Assistant, keyed by Music Assistant player id. Music Assistant
 * stores no location, so this is the only source; an empty map means "no locations known" (no
 * Home Assistant connection set, or the read failed) and the speaker list then shows no floor headings.
 */
interface PlayerLocationSource {
    val locations: StateFlow<Map<String, PlayerLocation>>

    /** Re-reads Home Assistant in the background. On failure the last good result is kept. */
    fun refresh()
}
