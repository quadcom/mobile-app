package io.music_assistant.client.ui.compose.home.players.speakers

import io.music_assistant.client.data.location.PlayerLocation
import io.music_assistant.client.data.location.SpeakerGrouping
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
    fun aBoundRoomIsGreyedAndNamesItsLeader() {
        val leader = room("L", "Kitchen", playing = true, members = setOf("L", "m"))
        val member = room("m")
        val free = room("f")
        val players = listOf(leader, member, free)
        val bound = boundLeaders(players)
        assertEquals("Kitchen", groupLeaderName(member, players, bound))
        assertEquals(listOf("L"), playingRoomIds(players, bound))
        assertEquals(listOf("m"), membersOf(leader, players, bound).map { it.playerId })
    }

    @Test
    fun aTapOnACardSelectsItsGroupLeaderOrItself() {
        val leader = room("L", "Kitchen", playing = true, members = setOf("L", "m"))
        val member = room("m")
        val free = room("f")
        val bound = boundLeaders(listOf(leader, member, free))
        assertEquals("L", cardTapTarget(member, bound, unavailable = false))
        assertEquals("f", cardTapTarget(free, bound, unavailable = false))
        assertNull(cardTapTarget(free, bound, unavailable = true))
        assertNull(cardTapTarget(member, bound, unavailable = true))
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

    private fun floor(vararg rooms: PlayerData, other: Boolean = false) =
        FloorSection(if (other) "other" else "floor:F", if (other) null else "F", other, rooms.toList())

    private fun unavailable(p: PlayerData) = p.copy(player = p.player.copy(isAvailable = false))

    @Test
    fun floorActionAddsWhenSomeAreOut() {
        val leader = room("L", members = setOf("L", "a"))
        val a = room("a")
        val b = room("b")
        val bound = boundLeaders(listOf(leader, a, b))
        assertEquals(FloorAction.AddAll(listOf("b")), floorGroupAction(floor(leader, a, b), leader, bound))
    }

    @Test
    fun floorActionRemovesWhenAllAreIn() {
        val leader = room("L", members = setOf("L", "a", "b"), canGroup = listOf("a", "b"))
        val a = room("a")
        val b = room("b")
        val bound = boundLeaders(listOf(leader, a, b))
        assertEquals(FloorAction.RemoveAll(listOf("a", "b")), floorGroupAction(floor(a, b), leader, bound))
    }

    @Test
    fun floorActionNeverRemovesALockedMember() {
        val base = room("L", members = setOf("L", "a", "b"), canGroup = listOf("a", "b"))
        val leader = base.copy(
            childrenBinds = base.childrenBinds.map { if (it.id == "a") it.copy(isManageable = false) else it },
        )
        val a = room("a")
        val b = room("b")
        val bound = boundLeaders(listOf(leader, a, b))
        assertEquals(FloorAction.RemoveAll(listOf("b")), floorGroupAction(floor(a, b), leader, bound))
    }

    @Test
    fun floorActionNeverAddsAnUnavailableRoom() {
        val leader = room("L")
        val a = unavailable(room("a"))
        val b = room("b")
        assertEquals(FloorAction.AddAll(listOf("b")), floorGroupAction(floor(a, b), leader, emptyMap()))
        assertEquals(FloorAction.None, floorGroupAction(floor(a), leader, emptyMap()))
    }

    @Test
    fun floorActionIsNoneForOther() {
        val leader = room("L")
        assertEquals(FloorAction.None, floorGroupAction(floor(room("a"), other = true), leader, emptyMap()))
    }

    @Test
    fun floorActionDropsDuplicateIds() {
        val leader = room("L")
        val a = room("a")
        assertEquals(FloorAction.AddAll(listOf("a")), floorGroupAction(floor(a, a), leader, emptyMap()))
    }

    private val groupingRooms = listOf(room("k"), room("b"), room("g"), room("n"))
    private val groupingLocations = mapOf(
        "k" to PlayerLocation("Kitchen", "Ground", 0, listOf("Party", "Loud")),
        "b" to PlayerLocation("bedroom", "Upper", 1, listOf("Party")),
        "g" to PlayerLocation("Garage", null, null),
    )

    @Test
    fun noGroupingGivesOnePlainSection() {
        val sections = speakerSections(groupingRooms, groupingLocations, SpeakerGrouping.NONE)
        assertEquals(listOf("all"), sections.map { it.key })
        assertNull(sections.single().title)
        assertEquals(listOf("b", "g", "k", "n"), sections.single().rooms.map { it.playerId })
    }

    @Test
    fun floorGroupingMatchesFloorSections() {
        assertEquals(
            floorSections(groupingRooms, groupingLocations),
            speakerSections(groupingRooms, groupingLocations, SpeakerGrouping.FLOOR),
        )
    }

    @Test
    fun areaGroupingRunsAToZWithOtherLast() {
        val sections = speakerSections(groupingRooms, groupingLocations, SpeakerGrouping.AREA)
        assertEquals(listOf("area:bedroom", "area:Garage", "area:Kitchen", "other"), sections.map { it.key })
        assertTrue(sections.last().isOther)
        assertEquals(listOf("n"), sections.last().rooms.map { it.playerId })
    }

    @Test
    fun labelGroupingRepeatsARoomUnderEachLabel() {
        val sections = speakerSections(groupingRooms, groupingLocations, SpeakerGrouping.LABEL)
        assertEquals(listOf("label:Loud", "label:Party", "other"), sections.map { it.key })
        assertEquals(listOf("b", "k"), sections[1].rooms.map { it.playerId })
        assertEquals(listOf("k"), sections[0].rooms.map { it.playerId })
        assertEquals(listOf("g", "n"), sections.last().rooms.map { it.playerId })
    }

    @Test
    fun noLocationsGivesOnePlainSectionForAnyGrouping() {
        val sections = speakerSections(groupingRooms, emptyMap(), SpeakerGrouping.LABEL)
        assertEquals(listOf("all"), sections.map { it.key })
    }
}
