package io.music_assistant.client.data.location

import kotlinx.coroutines.flow.StateFlow

/**
 * Where a Music Assistant player sits in the home, as Music Assistant reports it from Home
 * Assistant's floors, areas and labels. Any field is null or empty when Home Assistant has none.
 */
data class PlayerLocation(
    val areaName: String?,
    val floorName: String?,
    /** Home Assistant's floor `level`; higher floors sort first in the speaker list. */
    val floorLevel: Int?,
    val labels: List<String> = emptyList(),
)

/** What the speaker list groups rooms by; the server's setting decides. */
enum class SpeakerGrouping { FLOOR, AREA, LABEL, NONE }

/**
 * Player locations from Music Assistant (`hass/player_locations`), keyed by player id. An empty map
 * means "no locations known" (the server has no Home Assistant link, lacks the command, or the read
 * failed) and the speaker list then shows no headings.
 */
interface PlayerLocationSource {
    val locations: StateFlow<Map<String, PlayerLocation>>
    val grouping: StateFlow<SpeakerGrouping>

    /** Re-reads the server in the background. On failure the last good result is kept. */
    fun refresh()
}
