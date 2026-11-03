package io.music_assistant.client.data.location

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.HttpClientFactory
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Why the last Home Assistant read failed; the settings screen turns it into words. */
enum class HaFailure { BAD_ADDRESS, AUTH, TIMEOUT, UNREACHABLE, BAD_ANSWER }

sealed interface HaStatus {
    data object Idle : HaStatus
    data object Working : HaStatus
    data class Connected(val speakersPlaced: Int) : HaStatus
    data class Failed(val reason: HaFailure) : HaStatus
}

/** [PlayerLocation] as stored in the cache; the contract type is not serialisable. */
@Serializable
private data class CachedLocation(val area: String? = null, val floor: String? = null, val level: Int? = null)

private class HaException(val failure: HaFailure) : Exception()

/**
 * Reads each Music Assistant player's area and floor from the Home Assistant websocket API.
 * The map stays empty until a read succeeds, and a failed read keeps the last good map.
 */
class HomeAssistantLocationSource(
    private val settings: SettingsRepository,
    private val httpClientFactory: HttpClientFactory,
    private val scope: CoroutineScope,
) : PlayerLocationSource {
    private val logger = Logger.withTag("HaLocations")
    private val cacheSerializer = MapSerializer(String.serializer(), CachedLocation.serializer())

    private val _locations = MutableStateFlow(loadCache())
    override val locations: StateFlow<Map<String, PlayerLocation>> = _locations.asStateFlow()

    private val _status = MutableStateFlow<HaStatus>(HaStatus.Idle)
    val status: StateFlow<HaStatus> = _status.asStateFlow()

    @Volatile
    private var job: Job? = null

    override fun refresh() {
        if (job?.isActive == true) return
        if (settings.haAddress.value.isBlank() || settings.haToken.value.isBlank()) return
        job = scope.launch { readOnce() }
    }

    /** New credentials make the old map meaningless, so drop it and any read in flight, then read again. */
    fun saveCredentials(address: String, token: String) {
        job?.cancel()
        settings.setHaAddress(address.trim())
        settings.setHaToken(token.trim())
        settings.haLocationsJson = ""
        _locations.value = emptyMap()
        _status.value = HaStatus.Idle
        refresh()
    }

    private suspend fun readOnce() {
        _status.value = HaStatus.Working
        try {
            val map = withTimeout(TIMEOUT_MS) { fetch() }
            _locations.value = map
            settings.haLocationsJson = myJson.encodeToString(
                cacheSerializer,
                map.mapValues { CachedLocation(it.value.areaName, it.value.floorName, it.value.floorLevel) },
            )
            _status.value = HaStatus.Connected(map.size)
        } catch (ignored: TimeoutCancellationException) {
            fail(HaFailure.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HaException) {
            fail(e.failure)
        } catch (e: Exception) {
            // Class name only: the message of a network error can carry the address.
            logger.w { "Home Assistant read failed: ${e::class.simpleName}" }
            _status.value = HaStatus.Failed(
                if (e is SerializationException || e is IllegalArgumentException) {
                    HaFailure.BAD_ANSWER
                } else {
                    HaFailure.UNREACHABLE
                },
            )
        }
    }

    private fun fail(reason: HaFailure) {
        logger.w { "Home Assistant read failed: $reason" }
        _status.value = HaStatus.Failed(reason)
    }

    // One linear handshake with a distinct failure at each step reads more plainly than split helpers.
    @Suppress("ThrowsCount")
    private suspend fun fetch(): Map<String, PlayerLocation> {
        val url = haWebSocketUrl(settings.haAddress.value) ?: throw HaException(HaFailure.BAD_ADDRESS)
        val token = settings.haToken.value
        val client = httpClientFactory.create { install(WebSockets) }
        try {
            val session = client.webSocketSession(url)
            try {
                suspend fun next(): JsonObject {
                    while (true) {
                        val frame = session.incoming.receive()
                        if (frame is Frame.Text) return myJson.parseToJsonElement(frame.readText()).jsonObject
                    }
                }

                suspend fun send(json: JsonObject) = session.send(Frame.Text(json.toString()))

                if (next()["type"]?.jsonPrimitive?.content != "auth_required") {
                    throw HaException(HaFailure.BAD_ANSWER)
                }
                send(JsonObject(mapOf("type" to JsonPrimitive("auth"), "access_token" to JsonPrimitive(token))))
                when (next()["type"]?.jsonPrimitive?.content) {
                    "auth_ok" -> Unit
                    "auth_invalid" -> throw HaException(HaFailure.AUTH)
                    else -> throw HaException(HaFailure.BAD_ANSWER)
                }

                val commands = listOf(
                    FLOORS to "config/floor_registry/list",
                    AREAS to "config/area_registry/list",
                    DEVICES to "config/device_registry/list",
                )
                commands.forEach { (id, type) ->
                    send(JsonObject(mapOf("id" to JsonPrimitive(id), "type" to JsonPrimitive(type))))
                }
                val results = mutableMapOf<Int, JsonElement>()
                while (results.size < commands.size) {
                    val msg = next()
                    if (msg["type"]?.jsonPrimitive?.content != "result") continue
                    val id = msg["id"]?.jsonPrimitive?.int ?: continue
                    if (msg["success"]?.jsonPrimitive?.boolean != true) throw HaException(HaFailure.BAD_ANSWER)
                    results[id] = msg["result"] ?: JsonArray(emptyList())
                }

                fun <T> decode(id: Int, serializer: KSerializer<T>): List<T> =
                    myJson.decodeFromJsonElement(ListSerializer(serializer), results.getValue(id))
                return mapPlayerLocations(
                    decode(FLOORS, HaFloor.serializer()),
                    decode(AREAS, HaArea.serializer()),
                    decode(DEVICES, HaDevice.serializer()),
                )
            } finally {
                runCatching { session.close() }
            }
        } finally {
            client.close()
        }
    }

    private fun loadCache(): Map<String, PlayerLocation> =
        settings.haLocationsJson.takeIf { it.isNotBlank() }?.let { json ->
            runCatching { myJson.decodeFromString(cacheSerializer, json) }
                .getOrNull()
                ?.mapValues { PlayerLocation(it.value.area, it.value.floor, it.value.level) }
        } ?: emptyMap()

    private companion object {
        const val TIMEOUT_MS = 10_000L

        // Message ids of the three registry reads; HA answers each with the id it was sent.
        const val FLOORS = 1
        const val AREAS = 2
        const val DEVICES = 3
    }
}
