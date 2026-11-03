package io.music_assistant.client.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class HaFloor(
    @SerialName("floor_id") val floorId: String,
    val name: String,
    val level: Int? = null,
)

@Serializable
data class HaArea(
    @SerialName("area_id") val areaId: String,
    val name: String,
    @SerialName("floor_id") val floorId: String? = null,
)

@Serializable
data class HaDevice(
    // Each entry is a [domain, id] pair; other integrations may put non-strings in it, so keep raw JSON.
    val identifiers: List<List<JsonElement>> = emptyList(),
    @SerialName("area_id") val areaId: String? = null,
)

private const val MUSIC_ASSISTANT_DOMAIN = "music_assistant"

/** The Music Assistant player ids that a device carries; devices of other integrations give none. */
private fun HaDevice.playerIds(): List<String> = identifiers.mapNotNull { pair ->
    val domain = (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull
    val id = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull
    id?.takeIf { domain == MUSIC_ASSISTANT_DOMAIN }
}

/** Joins the three Home Assistant registries into player id -> location. Pure, so it is testable. */
fun mapPlayerLocations(
    floors: List<HaFloor>,
    areas: List<HaArea>,
    devices: List<HaDevice>,
): Map<String, PlayerLocation> {
    val floorById = floors.associateBy { it.floorId }
    val areaById = areas.associateBy { it.areaId }
    return buildMap {
        devices.forEach { device ->
            val area = device.areaId?.let(areaById::get)
            val floor = area?.floorId?.let(floorById::get)
            val location = PlayerLocation(area?.name, floor?.name, floor?.level)
            device.playerIds().forEach { put(it, location) }
        }
    }
}

/** `http(s)://host:port/` becomes `ws(s)://host:port/api/websocket`; null when the address is not http(s). */
fun haWebSocketUrl(address: String): String? {
    val trimmed = address.trim().trimEnd('/')
    val scheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "wss"
        trimmed.startsWith("http://", ignoreCase = true) -> "ws"
        else -> return null
    }
    return "$scheme://${trimmed.substringAfter("://")}/api/websocket"
}
