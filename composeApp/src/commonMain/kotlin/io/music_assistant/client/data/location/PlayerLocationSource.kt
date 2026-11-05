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

/** The colour that marks a group of speakers; the order is the order a new group takes them in. */
enum class GroupColour(val wire: String, val argb: Long) {
    ORANGE("orange", 0xFFFB923C),
    PURPLE("purple", 0xFFA855F7),
    GREEN("green", 0xFF22C55E),
    PINK("pink", 0xFFEC4899),
    YELLOW("yellow", 0xFFFACC15),
    TEAL("teal", 0xFF14B8A6),
    ;

    companion object {
        fun fromWire(word: String): GroupColour? = entries.firstOrNull { it.wire == word.lowercase() }
    }
}

/**
 * Player locations from Music Assistant (`hass/player_locations`), keyed by player id. An empty map
 * means "no locations known" (the server has no Home Assistant link, lacks the command, or the read
 * failed) and the speaker list then shows no headings.
 */
interface PlayerLocationSource {
    val locations: StateFlow<Map<String, PlayerLocation>>
    val grouping: StateFlow<SpeakerGrouping>

    /** Group leader's player id to its colour, as the server keeps it; empty when the server sends none. */
    val groupColours: StateFlow<Map<String, GroupColour>>

    /** Re-reads the server in the background. On failure the last good result is kept. */
    fun refresh()
}
