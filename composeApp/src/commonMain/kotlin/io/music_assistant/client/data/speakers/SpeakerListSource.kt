package io.music_assistant.client.data.speakers

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.events.ProviderEvent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull

/** The colour that marks a group of speakers; the server names it, the app only paints it. */
enum class GroupColour(val wire: String, val argb: Long) {
    ORANGE("orange", 0xFFFB923C),
    PURPLE("purple", 0xFFA855F7),
    GREEN("green", 0xFF22C55E),
    PINK("pink", 0xFFEC4899),
    YELLOW("yellow", 0xFFFACC15),
    TEAL("teal", 0xFF14B8A6),
    ;

    companion object {
        fun fromWire(word: String?): GroupColour? = entries.firstOrNull { it.wire == word?.lowercase() }
    }
}

enum class SectionKind(val wire: String) {
    FLOOR("floor"),
    AREA("area"),
    LABEL("label"),
    OTHER("other"),
    ALL("all"),
    ;

    companion object {
        // An unknown kind is drawn like any titled section rather than dropped.
        fun fromWire(word: String?): SectionKind = entries.firstOrNull { it.wire == word?.lowercase() } ?: AREA
    }
}

/** One block of the list, in the server's order. [rooms] are player ids; a room may sit in several sections. */
data class SpeakerSection(
    val key: String,
    val kind: SectionKind,
    val title: String?,
    val rooms: List<String>,
)

/** What the server says about one room; the name and live state come from the app's own player data. */
data class SpeakerRoom(
    val available: Boolean,
    /** Set when the room is a greyed member of another room's group. */
    val groupLeader: String?,
    /** The rooms the group editor offers for this room as a leader. */
    val groupWith: List<String>,
    /** Set when the room is a two-speaker group marked left and right. */
    val isStereoPair: Boolean = false,
    /** The server's name for the room; null from a server that does not send it. */
    val name: String? = null,
    /** The Home Assistant area the room sits in, if any. */
    val area: String? = null,
    /** Set when the room is a permanent group or a stereo pair. */
    val isPermanentGroup: Boolean = false,
    /** "permanent", "stereo_pair" or "dynamic"; null from a server that does not say. */
    val groupKind: String? = null,
)

/** A group made on the fly; members are in name order. */
data class SpeakerGroup(val leader: String, val colour: GroupColour?, val members: List<String>)

/** A group that stays; a stereo pair is one with a left speaker chosen. */
data class PermanentGroup(
    val player: String,
    val name: String,
    val members: List<String>,
    val isStereoPair: Boolean = false,
    val stereoLeft: String? = null,
)

/** The finished speaker list from `hass/speaker_list`. */
data class SpeakerList(
    val revision: Long,
    val sections: List<SpeakerSection>,
    val rooms: Map<String, SpeakerRoom>,
    val groups: List<SpeakerGroup>,
    val permanentGroups: List<PermanentGroup> = emptyList(),
) {
    /** Room id to its group leader's id, for every room the server marks as a member. */
    val bound: Map<String, String> = rooms.mapNotNull { (id, room) -> room.groupLeader?.let { id to it } }.toMap()

    /** The colour of the group this room leads, or null. */
    fun ledColour(roomId: String): GroupColour? = groups.firstOrNull { it.leader == roomId }?.colour

    /** The colour of the group this room is in (its own if it leads, else its leader's), or null. */
    fun colourOf(roomId: String): GroupColour? =
        groups.firstOrNull { roomId == it.leader || roomId in it.members }?.colour
}

@Serializable
private data class SpeakerListReply(
    val revision: Long = 0,
    val sections: List<SectionDto> = emptyList(),
    val rooms: Map<String, RoomDto> = emptyMap(),
    val groups: List<GroupDto> = emptyList(),
    @SerialName("permanent_groups") val permanentGroups: List<PermanentGroupDto> = emptyList(),
)

@Serializable
private data class SectionDto(
    val key: String = "",
    val kind: String? = null,
    val title: String? = null,
    val rooms: List<String> = emptyList(),
)

@Serializable
private data class RoomDto(
    val available: Boolean = true,
    @SerialName("group_leader") val groupLeader: String? = null,
    @SerialName("group_with") val groupWith: List<String> = emptyList(),
    @SerialName("is_stereo_pair") val isStereoPair: Boolean = false,
    val name: String? = null,
    val area: String? = null,
    @SerialName("is_permanent_group") val isPermanentGroup: Boolean = false,
    @SerialName("group_kind") val groupKind: String? = null,
)

@Serializable
private data class PermanentGroupDto(
    val player: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    @SerialName("is_stereo_pair") val isStereoPair: Boolean = false,
    @SerialName("stereo_left") val stereoLeft: String? = null,
)

@Serializable
private data class GroupDto(
    val leader: String = "",
    val colour: String? = null,
    val members: List<String> = emptyList(),
)

/** Decodes the `hass/speaker_list` reply; unknown keys are ignored and missing ones take plain defaults. */
internal fun decodeSpeakerList(json: String): SpeakerList {
    val reply = myJson.decodeFromString<SpeakerListReply>(json)
    return SpeakerList(
        revision = reply.revision,
        sections = reply.sections.map { SpeakerSection(it.key, SectionKind.fromWire(it.kind), it.title, it.rooms) },
        rooms = reply.rooms.mapValues { (_, r) ->
            SpeakerRoom(
                available = r.available,
                groupLeader = r.groupLeader,
                groupWith = r.groupWith,
                isStereoPair = r.isStereoPair,
                name = r.name,
                area = r.area,
                isPermanentGroup = r.isPermanentGroup,
                groupKind = r.groupKind,
            )
        },
        groups = reply.groups.map { SpeakerGroup(it.leader, GroupColour.fromWire(it.colour), it.members) },
        permanentGroups = reply.permanentGroups.map {
            PermanentGroup(it.player, it.name, it.members, it.isStereoPair, it.stereoLeft)
        },
    )
}

/** A Home Assistant floor; the server already sends floors in Home Assistant's order, so [level] is only shown. */
data class HaFloor(val floorId: String, val name: String, val level: Int? = null)

data class HaArea(val areaId: String, val name: String, val floorId: String? = null)

/** The `hass/areas` reply; [playerAreas] maps a player id to its area id, or null for none. */
data class HaAreas(val floors: List<HaFloor>, val areas: List<HaArea>, val playerAreas: Map<String, String?>)

@Serializable
private data class FloorDto(@SerialName("floor_id") val floorId: String, val name: String = "", val level: Int? = null)

@Serializable
private data class AreaDto(
    @SerialName("area_id") val areaId: String,
    val name: String = "",
    @SerialName("floor_id") val floorId: String? = null,
)

@Serializable
private data class AreasDto(
    val floors: List<FloorDto> = emptyList(),
    val areas: List<AreaDto> = emptyList(),
    @SerialName("player_areas") val playerAreas: Map<String, String?> = emptyMap(),
)

/** Decodes the `hass/areas` result; null when it is absent or not in that shape. */
internal fun decodeAreas(result: JsonElement?): HaAreas? {
    if (result == null) return null
    val dto = runCatching { myJson.decodeFromJsonElement<AreasDto>(result) }.getOrNull() ?: return null
    return HaAreas(
        floors = dto.floors.map { HaFloor(it.floorId, it.name, it.level) },
        areas = dto.areas.map { HaArea(it.areaId, it.name, it.floorId) },
        playerAreas = dto.playerAreas,
    )
}

/** The new room's id from the `hass/areas/create` result, an object `{"area_id": "..."}`; null if absent. */
internal fun decodeCreatedAreaId(result: JsonElement?): String? =
    ((result as? JsonObject)?.get("area_id") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Whether a `speaker_list` event is worth a re-read: its revision differs from the held one (a restarted
 * server counts again from zero, so "newer" would miss its changes). An event whose revision cannot be read
 * counts as changed, because a needless re-read is cheap and a missed one is not.
 */
internal fun isChangedRevision(eventData: JsonObject?, held: Long?): Boolean {
    val revision = (eventData?.get("revision") as? JsonPrimitive)?.longOrNull ?: return true
    return revision != held
}

/**
 * The speaker list as the Music Assistant server builds it (`hass/speaker_list`). Holds null when the
 * server has no such command (a stock server) or nothing was read yet; the last good list is cached in
 * settings so the list is there at app start.
 */
class SpeakerListSource(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("SpeakerList")

    private val _list = MutableStateFlow(
        settings.speakerListJson.takeIf { it.isNotEmpty() }
            ?.let { runCatching { decodeSpeakerList(it) }.getOrNull() },
    )
    val list: StateFlow<SpeakerList?> = _list

    private var job: Job? = null

    init {
        // The event carries only a revision, because the list is per user; the list is read again.
        scope.launch {
            apiClient.events
                .filterIsInstance<ProviderEvent>()
                .filter { it.objectId?.endsWith(EVENT_SUFFIX) == true }
                .collect { if (isChangedRevision(it.data, _list.value?.revision)) refresh() }
        }
    }

    /** Re-reads the server in the background. On failure the last good list is kept. */
    fun refresh() {
        // A read that began before the change would answer with the old list, so a new one replaces it.
        job?.cancel()
        job = scope.launch {
            try {
                val answer = withTimeout(TIMEOUT_MS) { apiClient.sendRequest(Request.Player.speakerList()) }
                    .getOrThrow()
                val json = answer.result?.toString() ?: error("empty reply")
                _list.value = decodeSpeakerList(json)
                settings.speakerListJson = json
            } catch (e: CancellationException) {
                // A timeout is a failed read; any other cancellation must reach the scope.
                if (e !is TimeoutCancellationException) throw e
                log.w { "Could not read the speaker list: timeout" }
            } catch (e: Exception) {
                // Class name only: a message could carry server details.
                log.w { "Could not read the speaker list: ${e::class.simpleName}" }
            }
        }
    }

    /** Plays a short ding on that one speaker. Safe to repeat. */
    suspend fun identifyStereoSpeaker(playerId: String): Result<Unit> =
        call(Request.Player.stereoPairIdentify(playerId))

    /** Saves the pair with [leftPlayerId] on the left, or turns the pair off when it is null. */
    suspend fun setStereoPair(groupId: String, leftPlayerId: String?): Result<Unit> =
        call(Request.Player.stereoPairSet(groupId, leftPlayerId))

    /** The rooms and floors, and which room each player is in. */
    suspend fun areas(): Result<HaAreas> =
        call(Request.Player.hassAreas()) { decodeAreas(it.result) }
            // The blank message makes the dialog show its own words.
            .mapCatching { it ?: error("") }

    suspend fun setPlayerArea(playerId: String, areaId: String?): Result<Unit> =
        call(Request.Player.hassAreasSetPlayer(playerId, areaId))

    /** Makes a room, on an existing floor or a new one, and returns its area id. */
    suspend fun createArea(name: String, floorId: String?, newFloorName: String?): Result<String> =
        call(Request.Player.hassAreasCreate(name, floorId, newFloorName)) { decodeCreatedAreaId(it.result) }
            .mapCatching { it ?: error("") }

    private suspend fun call(request: Request): Result<Unit> = call(request) { }

    // The server reports a refusal as an answer with an error_code, not as a failed send. Only its own
    // "details" text is passed on; any other failure keeps only its cause, with no message, so the dialog
    // shows its own words rather than a raw technical one.
    private suspend fun <T> call(request: Request, read: (Answer) -> T): Result<T> = try {
        val answer = withTimeout(TIMEOUT_MS) { apiClient.sendRequest(request) }.getOrThrow()
        if (answer.json.containsKey("error_code")) {
            val details = (answer.json["details"] as? JsonPrimitive)?.contentOrNull
            Result.failure(IllegalStateException(details))
        } else {
            Result.success(read(answer))
        }
    } catch (e: CancellationException) {
        // A timeout is a failed call; any other cancellation must reach the scope.
        if (e !is TimeoutCancellationException) throw e
        Result.failure(IllegalStateException(null, e))
    } catch (e: Exception) {
        log.w { "Stereo pair call failed: ${e::class.simpleName}" }
        Result.failure(IllegalStateException(null, e))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val EVENT_SUFFIX = "/speaker_list"
    }
}
