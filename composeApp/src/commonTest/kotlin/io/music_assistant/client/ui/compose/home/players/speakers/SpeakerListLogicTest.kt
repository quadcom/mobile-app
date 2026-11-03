package io.music_assistant.client.ui.compose.home.players.speakers

import io.music_assistant.client.data.location.PlayerLocation
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerData.ChildBind
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeakerListLogicTest {
    private fun room(
        id: String,
        name: String = id,
        playing: Boolean = false,
        members: Set<String>? = null,
        canGroup: List<String> = emptyList(),
    ): PlayerData {
        val base = PlayerDataFixtures.playerData(name = name)
        return base.copy(
            player = base.player.copy(
                id = id,
                isPlaying = playing,
                groupMembers = members,
            ),
            childrenBinds = canGroup.map { bindId ->
                ChildBind(bindId, id, null, false, null, bindId, members?.contains(bindId) == true, true)
            },
        )
    }

    @Test
    fun newPlayingRoomsAreAppended() {
        assertEquals(listOf("a", "b", "c"), nextTopOrder(listOf("a", "b"), listOf("b", "c"), "a"))
    }

    @Test
    fun changingTheActiveRoomKeepsTheOrder() {
        val order = listOf("a", "b", "c")
        assertEquals(order, nextTopOrder(order, listOf("a", "b", "c"), "c"))
        assertEquals(order, nextTopOrder(order, listOf("a", "b", "c"), "a"))
    }

    @Test
    fun aRoomThatStoppedAndIsNotActiveIsDropped() {
        assertEquals(listOf("a", "c"), nextTopOrder(listOf("a", "b", "c"), listOf("a", "c"), "a"))
        assertEquals(listOf("a", "b", "c"), nextTopOrder(listOf("a", "b", "c"), listOf("a", "c"), "b"))
    }

    @Test
    fun anIdleActiveRoomIsListed() {
        assertEquals(listOf("x"), nextTopOrder(emptyList(), emptyList(), "x"))
    }

    @Test
    fun floorsSortByLevelDescendingAndOtherComesLast() {
        val rooms = listOf(room("k"), room("b"), room("g"), room("n"))
        val locations = mapOf(
            "k" to PlayerLocation("Kitchen", "Ground", 0),
            "b" to PlayerLocation("Bedroom", "Upstairs", 1),
            "g" to PlayerLocation(null, "Ground", 0),
        )
        val sections = floorSections(rooms, locations)
        assertEquals(listOf("Upstairs", "Ground", null), sections.map { it.title })
        assertTrue(sections.last().isOther)
        // "g" has no area, so it joins "n" under Other.
        assertEquals(listOf("g", "n"), sections.last().rooms.map { it.playerId })
    }

    @Test
    fun cardsInAFloorAreSortedByName() {
        val rooms = listOf(room("1", "Zed"), room("2", "alpha"))
        val loc = PlayerLocation("A", "F", 0)
        val sections = floorSections(rooms, mapOf("1" to loc, "2" to loc))
        assertEquals(listOf("alpha", "Zed"), sections.single().rooms.map { it.player.name })
    }

    @Test
    fun noLocationsMeansOneBlockWithoutHeadings() {
        val sections = floorSections(listOf(room("b"), room("a")), emptyMap())
        assertEquals(1, sections.size)
        assertNull(sections.single().title)
        assertTrue(!sections.single().isOther)
    }

    @Test
    fun aBoundRoomIsGreyedAndIgnoresTaps() {
        val leader = room("L", "Kitchen", playing = true, members = setOf("L", "m"))
        val member = room("m")
        val free = room("f")
        val players = listOf(leader, member, free)
        val bound = boundLeaders(players)
        assertEquals("Kitchen", groupLeaderName(member, players, bound))
        assertTrue(cardTapsIgnored(groupLeaderName(member, players, bound)))
        assertTrue(!cardTapsIgnored(groupLeaderName(free, players, bound)))
        assertEquals(listOf("L"), playingRoomIds(players, bound))
        assertEquals(listOf("m"), membersOf(leader, players, bound).map { it.playerId })
    }

    @Test
    fun editorTagRule() {
        val leader = room(
            "L",
            "Kitchen",
            playing = true,
            members = setOf("L", "in"),
            canGroup = listOf("in", "solo", "other", "idle"),
        )
        val inGroup = room("in")
        val solo = room("solo", playing = true)
        val otherLeader = room("O", "Patio", playing = true, members = setOf("O", "other"))
        val other = room("other")
        val idle = room("idle")
        val players = listOf(leader, inGroup, solo, otherLeader, other, idle)

        assertEquals(EditorTag.ThisRoom, editorTag(leader, leader, players))
        assertEquals(EditorTag.Named("Kitchen"), editorTag(inGroup, leader, players))
        assertEquals(EditorTag.Playing, editorTag(solo, leader, players))
        assertEquals(EditorTag.Named("Patio"), editorTag(other, leader, players))
        assertEquals(EditorTag.None, editorTag(idle, leader, players))
    }

    @Test
    fun editorOffersOnlyRoomsTheLeaderCanGroupWith() {
        val leader = room("L", canGroup = listOf("a"))
        val a = room("a")
        val b = room("b")
        assertEquals(listOf("a"), offeredRooms(leader, listOf(leader, a, b)).map { it.playerId })
    }
}
