package io.music_assistant.client.data.location

import io.music_assistant.client.utils.myJson
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaRegistryMappingTest {
    private val floors = myJson.decodeFromString(
        ListSerializer(HaFloor.serializer()),
        """[{"floor_id":"ground","name":"Ground floor","level":0,"icon":null},
            {"floor_id":"upstairs","name":"Upstairs","level":1}]""",
    )
    private val areas = myJson.decodeFromString(
        ListSerializer(HaArea.serializer()),
        """[{"area_id":"kitchen","name":"Kitchen","floor_id":"ground"},
            {"area_id":"bedroom","name":"Bedroom","floor_id":"upstairs"},
            {"area_id":"shed","name":"Shed","floor_id":null}]""",
    )

    private fun devices(json: String) = myJson.decodeFromString(ListSerializer(HaDevice.serializer()), json)

    @Test
    fun musicAssistantDeviceMapsToAreaAndFloor() {
        val map = mapPlayerLocations(
            floors,
            areas,
            devices("""[{"id":"d1","area_id":"kitchen","identifiers":[["music_assistant","ma_player_1"]]}]"""),
        )
        assertEquals(PlayerLocation("Kitchen", "Ground floor", 0), map["ma_player_1"])
    }

    @Test
    fun otherIntegrationIsIgnored() {
        val map = mapPlayerLocations(
            floors,
            areas,
            devices("""[{"area_id":"kitchen","identifiers":[["cast","abc"],["hue",42]]}]"""),
        )
        assertTrue(map.isEmpty())
    }

    @Test
    fun deviceWithoutAreaHasNullAreaAndFloor() {
        val map = mapPlayerLocations(
            floors,
            areas,
            devices("""[{"area_id":null,"identifiers":[["music_assistant","ma_player_2"]]}]"""),
        )
        assertEquals(PlayerLocation(null, null, null), map["ma_player_2"])
    }

    @Test
    fun areaWithoutFloorHasNullFloor() {
        val map = mapPlayerLocations(
            floors,
            areas,
            devices("""[{"area_id":"shed","identifiers":[["music_assistant","ma_player_3"]]}]"""),
        )
        assertEquals("Shed", map["ma_player_3"]?.areaName)
        assertNull(map["ma_player_3"]?.floorName)
        assertNull(map["ma_player_3"]?.floorLevel)
    }

    @Test
    fun floorLevelIsCarried() {
        val map = mapPlayerLocations(
            floors,
            areas,
            devices("""[{"area_id":"bedroom","identifiers":[["music_assistant","ma_player_4"]]}]"""),
        )
        assertEquals(1, map["ma_player_4"]?.floorLevel)
    }

    @Test
    fun websocketUrlFollowsScheme() {
        assertEquals("ws://192.0.2.10:8123/api/websocket", haWebSocketUrl("http://192.0.2.10:8123/"))
        assertEquals("wss://ha.example.com/api/websocket", haWebSocketUrl(" https://ha.example.com "))
        assertNull(haWebSocketUrl("192.0.2.10:8123"))
    }
}
