package io.music_assistant.client.data.location

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class LocationsReply(
    val grouping: String? = null,
    val players: Map<String, LocationDto> = emptyMap(),
    @SerialName("group_colours") val groupColours: Map<String, String> = emptyMap(),
)

@Serializable
private data class LocationDto(
    val area: String? = null,
    val floor: String? = null,
    @SerialName("floor_level") val floorLevel: Int? = null,
    val labels: List<String> = emptyList(),
)

/** Decodes the `hass/player_locations` reply; an absent grouping means floor, an unknown one none. */
internal fun decodePlayerLocations(json: String): Pair<SpeakerGrouping, Map<String, PlayerLocation>> {
    val reply = myJson.decodeFromString<LocationsReply>(json)
    val grouping = when (reply.grouping?.lowercase()) {
        null, "floor" -> SpeakerGrouping.FLOOR
        "area" -> SpeakerGrouping.AREA
        "label" -> SpeakerGrouping.LABEL
        else -> SpeakerGrouping.NONE
    }
    return grouping to reply.players.mapValues { (_, p) ->
        PlayerLocation(p.area, p.floor, p.floorLevel, p.labels)
    }
}

/** Decodes the optional `group_colours` of the same reply; absent or unknown colour words are dropped. */
internal fun decodeGroupColours(json: String): Map<String, GroupColour> =
    myJson.decodeFromString<LocationsReply>(json).groupColours.mapNotNull { (leader, word) ->
        GroupColour.fromWire(word)?.let { leader to it }
    }.toMap()

/** Reads player locations from the Music Assistant server; the last good reply is cached in settings. */
class MusicAssistantLocationSource(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : PlayerLocationSource {
    private val log = Logger.withTag("PlayerLocations")

    private val cached = runCatching { decodePlayerLocations(settings.playerLocationsJson) }.getOrNull()
    private val _grouping = MutableStateFlow(cached?.first ?: SpeakerGrouping.NONE)
    private val _locations = MutableStateFlow(cached?.second.orEmpty())
    private val _groupColours = MutableStateFlow(
        runCatching { decodeGroupColours(settings.playerLocationsJson) }.getOrDefault(emptyMap()),
    )
    override val grouping: StateFlow<SpeakerGrouping> = _grouping
    override val groupColours: StateFlow<Map<String, GroupColour>> = _groupColours
    override val locations: StateFlow<Map<String, PlayerLocation>> = _locations

    private var job: Job? = null

    override fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val answer = withTimeout(TIMEOUT_MS) { apiClient.sendRequest(Request.Player.locations()) }
                    .getOrThrow()
                val json = answer.result?.toString() ?: error("empty reply")
                val (newGrouping, newLocations) = decodePlayerLocations(json)
                _grouping.value = newGrouping
                _locations.value = newLocations
                _groupColours.value = decodeGroupColours(json)
                settings.playerLocationsJson = json
            } catch (e: CancellationException) {
                // A timeout is a failed read; any other cancellation must reach the scope.
                if (e !is TimeoutCancellationException) throw e
                log.w { "Could not read player locations: timeout" }
            } catch (e: Exception) {
                // Class name only: a message could carry server details.
                log.w { "Could not read player locations: ${e::class.simpleName}" }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
