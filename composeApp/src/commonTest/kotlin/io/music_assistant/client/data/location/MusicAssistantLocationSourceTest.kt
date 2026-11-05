package io.music_assistant.client.data.location

import kotlin.test.Test
import kotlin.test.assertEquals

class MusicAssistantLocationSourceTest {
    private val players = """
        "players": {
            "p1": {"area": "Kitchen", "floor": "Ground", "floor_level": 0, "labels": ["Party"], "extra": 1},
            "p2": {"area": null, "floor": null, "floor_level": null, "labels": []}
        }
    """

    @Test
    fun decodesPlayersAndGrouping() {
        val (grouping, locations) = decodePlayerLocations("""{"grouping": "label", $players}""")
        assertEquals(SpeakerGrouping.LABEL, grouping)
        assertEquals(PlayerLocation("Kitchen", "Ground", 0, listOf("Party")), locations["p1"])
        assertEquals(PlayerLocation(null, null, null), locations["p2"])
    }

    @Test
    fun absentGroupingMeansFloor() {
        assertEquals(SpeakerGrouping.FLOOR, decodePlayerLocations("{$players}").first)
    }

    @Test
    fun unknownGroupingMeansNone() {
        assertEquals(SpeakerGrouping.NONE, decodePlayerLocations("""{"grouping": "zone", $players}""").first)
    }

    @Test
    fun areaGroupingIsRecognised() {
        assertEquals(SpeakerGrouping.AREA, decodePlayerLocations("""{"grouping": "area", "players": {}}""").first)
    }
}
