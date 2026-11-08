package io.music_assistant.client.data.speakers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeakerListSourceTest {
    private val full = """
        {
          "revision": 42, "grouping": "floor", "extra": true,
          "sections": [
            {"key": "floor:main", "kind": "floor", "title": "Main", "rooms": ["k", "l"]},
            {"key": "area:a", "kind": "area", "title": "Garage", "rooms": ["g"]},
            {"key": "label:party", "kind": "label", "title": "Party", "rooms": ["k"]},
            {"key": "other", "kind": "other", "title": null, "rooms": ["o"]},
            {"key": "all", "kind": "all", "title": null, "rooms": ["k"]}
          ],
          "rooms": {
            "k": {"name": "Kitchen", "available": true, "group_leader": null, "group_with": ["l", "g"]},
            "l": {"available": false, "group_leader": "k", "is_permanent_group": false, "group_with": []},
            "g": {}
          },
          "groups": [{"leader": "k", "colour": "Orange", "members": ["l"]}],
          "permanent_groups": []
        }
    """

    @Test
    fun decodesEveryKindAndTheRoomsAndGroups() {
        val list = decodeSpeakerList(full)
        assertEquals(42, list.revision)
        assertEquals(
            listOf(SectionKind.FLOOR, SectionKind.AREA, SectionKind.LABEL, SectionKind.OTHER, SectionKind.ALL),
            list.sections.map { it.kind },
        )
        assertEquals("Main", list.sections.first().title)
        assertNull(list.sections[3].title)
        assertEquals(listOf("k", "l"), list.sections.first().rooms)
        assertEquals(listOf("l", "g"), list.rooms["k"]?.groupWith)
        assertEquals(SpeakerRoom(false, "k", emptyList()), list.rooms["l"])
        assertEquals(listOf(SpeakerGroup("k", GroupColour.ORANGE, listOf("l"))), list.groups)
    }

    @Test
    fun missingFieldsTakePlainDefaults() {
        val list = decodeSpeakerList("{}")
        assertEquals(0, list.revision)
        assertTrue(list.sections.isEmpty() && list.rooms.isEmpty() && list.groups.isEmpty())
        // A room with nothing said is available and free.
        assertEquals(SpeakerRoom(true, null, emptyList()), decodeSpeakerList(full).rooms["g"])
    }

    @Test
    fun anUnknownKindIsDrawnAsATitledSectionAndAnUnknownColourIsNone() {
        val list = decodeSpeakerList(
            """{"sections": [{"key": "z", "kind": "zone", "title": "Z", "rooms": []}],
                "groups": [{"leader": "a", "colour": "mauve", "members": []}]}""",
        )
        assertEquals(SectionKind.AREA, list.sections.single().kind)
        assertNull(list.groups.single().colour)
    }

    @Test
    fun membersAreBoundToTheirLeaderAndTakeItsColour() {
        val list = decodeSpeakerList(full)
        assertEquals(mapOf("l" to "k"), list.bound)
        assertEquals(GroupColour.ORANGE, list.colourOf("l"))
        assertEquals(GroupColour.ORANGE, list.colourOf("k"))
        assertEquals(GroupColour.ORANGE, list.ledColour("k"))
        assertNull(list.ledColour("l"))
        assertNull(list.colourOf("g"))
    }

    @Test
    fun decodesTheStereoPairFlagOnRoomsAndPermanentGroups() {
        val list = decodeSpeakerList(
            """{"rooms": {"p": {"is_stereo_pair": true}, "q": {}},
                "permanent_groups": [
                  {"player": "p", "name": "Bedroom", "members": ["a", "b"], "is_stereo_pair": true, "stereo_left": "a"},
                  {"player": "r", "name": "Den", "members": ["c", "d"]}
                ]}""",
        )
        assertTrue(list.rooms["p"]?.isStereoPair == true)
        assertFalse(list.rooms["q"]?.isStereoPair == true)
        assertEquals(
            listOf(
                PermanentGroup("p", "Bedroom", listOf("a", "b"), true, "a"),
                PermanentGroup("r", "Den", listOf("c", "d")),
            ),
            list.permanentGroups,
        )
    }

    @Test
    fun aServerWithoutStereoPairsSendsNoPermanentGroups() {
        assertTrue(decodeSpeakerList("{}").permanentGroups.isEmpty())
    }

    @Test
    fun decodesTheNameAreaAndPermanentGroupFlagOfARoom() {
        val room = decodeSpeakerList(
            """{"rooms": {"k": {"name": "Kitchen", "area": "Downstairs", "is_permanent_group": true}, "g": {}}}""",
        ).rooms
        assertEquals(SpeakerRoom(true, null, emptyList(), false, "Kitchen", "Downstairs", true), room["k"])
        assertNull(room["g"]?.name)
        assertFalse(room["g"]?.isPermanentGroup == true)
    }

    @Test
    fun readsTheNewAreaIdFromTheCreateAnswer() {
        assertEquals("kitchen", decodeCreatedAreaId(buildJsonObject { put("area_id", "kitchen") }))
        assertNull(decodeCreatedAreaId(buildJsonObject { put("other", "x") }))
        assertNull(decodeCreatedAreaId(buildJsonObject { put("area_id", "") }))
        assertNull(decodeCreatedAreaId(JsonPrimitive("kitchen")))
        assertNull(decodeCreatedAreaId(JsonNull))
        assertNull(decodeCreatedAreaId(null))
    }

    @Test
    fun decodesTheGroupKindOfARoom() {
        val room = decodeSpeakerList("""{"rooms": {"p": {"group_kind": "stereo_pair"}, "q": {}}}""").rooms
        assertEquals("stereo_pair", room["p"]?.groupKind)
        assertNull(room["q"]?.groupKind)
    }

    private fun areasJson(text: String) = Json.parseToJsonElement(text)

    @Test
    fun decodesFloorsRoomsAndWhereEachPlayerIs() {
        val areas = decodeAreas(
            areasJson(
                """{"floors": [{"floor_id": "up", "name": "Upstairs", "level": 1}],
                    "areas": [{"area_id": "bed", "name": "Bedroom", "floor_id": "up"}],
                    "player_areas": {"p1": "bed", "p2": null}}""",
            ),
        )
        assertEquals(listOf(HaFloor("up", "Upstairs", 1)), areas?.floors)
        assertEquals(listOf(HaArea("bed", "Bedroom", "up")), areas?.areas)
        assertEquals(mapOf("p1" to "bed", "p2" to null), areas?.playerAreas)
    }

    @Test
    fun aRoomOrFloorWithNoFloorOrLevelDecodesWithNulls() {
        val areas = decodeAreas(
            areasJson(
                """{"floors": [{"floor_id": "f", "name": "F"}],
                    "areas": [{"area_id": "a", "name": "A", "floor_id": null}]}""",
            ),
        )
        assertNull(areas?.floors?.single()?.level)
        assertNull(areas?.areas?.single()?.floorId)
    }

    @Test
    fun aPlayerMissingFromPlayerAreasHasNoRoom() {
        val areas = decodeAreas(areasJson("""{"floors": [], "areas": [], "player_areas": {"p1": "bed"}}"""))
        assertNull(areas?.playerAreas?.get("other"))
        assertTrue(decodeAreas(areasJson("{}"))?.playerAreas?.isEmpty() == true)
    }

    @Test
    fun malformedAreasDecodeToNull() {
        assertNull(decodeAreas(null))
        assertNull(decodeAreas(JsonNull))
        assertNull(decodeAreas(areasJson("""{"areas": [{"name": "no id"}]}""")))
        assertNull(decodeAreas(areasJson("[1, 2]")))
    }

    private fun event(revision: Any?) = buildJsonObject {
        when (revision) {
            is Int -> put("revision", revision)
            is String -> put("revision", revision)
        }
    }

    @Test
    fun onlyAChangedRevisionIsReRead() {
        assertTrue(isChangedRevision(event(43), 42))
        assertFalse(isChangedRevision(event(42), 42))
        assertTrue(isChangedRevision(event(41), 42))
    }

    @Test
    fun anUnreadableRevisionOrNoHeldListIsReRead() {
        assertTrue(isChangedRevision(event(null), 42))
        assertTrue(isChangedRevision(null, 42))
        assertTrue(isChangedRevision(event("soon"), 42))
        assertTrue(isChangedRevision(event(5), null))
    }

    @Test
    fun aRevisionSentAsATextNumberIsStillRead() {
        assertFalse(isChangedRevision(event("42"), 42))
        assertTrue(isChangedRevision(event("43"), 42))
    }
}
