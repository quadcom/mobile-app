package io.music_assistant.client.ui.compose.home.players.speakers

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerData.ChildBind
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.speakers.SectionKind
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.data.speakers.SpeakerRoom
import io.music_assistant.client.data.speakers.SpeakerSection
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
    fun aBoundRoomIsGreyedAndNamesItsLeader() {
        val leader = room("L", "Kitchen", playing = true, members = setOf("L", "m"))
        val member = room("m")
        val free = room("f")
        val players = listOf(leader, member, free)
        val bound = boundOf(players)
        assertEquals(listOf("L"), playingRoomIds(players, bound))
        assertEquals(listOf("m"), membersOf(leader, players, bound).map { it.playerId })
    }

    @Test
    fun aTapOnACardSelectsItsGroupLeaderOrItself() {
        val leader = room("L", "Kitchen", playing = true, members = setOf("L", "m"))
        val member = room("m")
        val free = room("f")
        val bound = boundOf(listOf(leader, member, free))
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

        assertEquals(EditorTag.ThisRoom, editorTag(leader, leader, players, boundOf(players)))
        assertEquals(EditorTag.Named("Kitchen"), editorTag(inGroup, leader, players, boundOf(players)))
        assertEquals(EditorTag.Playing, editorTag(solo, leader, players, boundOf(players)))
        assertEquals(EditorTag.Named("Patio"), editorTag(other, leader, players, boundOf(players)))
        assertEquals(EditorTag.None, editorTag(idle, leader, players, boundOf(players)))
    }

    @Test
    fun successorIsTheRoomNowHoldingTheOldMembers() {
        val old = room("L", members = setOf("L", "a", "b"))
        val next = room("a", members = setOf("a", "b"))
        val other = room("z", members = setOf("z", "y"))
        assertEquals("a", successorLeader("L", setOf("a", "b"), listOf(old, next, other)))
    }

    @Test
    fun noSuccessorWhileTheOldLeaderStillHoldsTheGroupOrNoneRemains() {
        val old = room("L", members = setOf("L", "a", "b"))
        assertNull(successorLeader("L", setOf("a", "b"), listOf(old, room("a"), room("b"))))
        // A room that merely went solo lists only itself.
        assertNull(successorLeader("L", setOf("a"), listOf(room("L"), room("a", members = setOf("a")))))
    }

    private fun floor(vararg rooms: PlayerData, other: Boolean = false) =
        FloorSection(if (other) "other" else "floor:F", if (other) null else "F", other, rooms.toList())

    private fun unavailable(p: PlayerData) = p.copy(player = p.player.copy(isAvailable = false))

    @Test
    fun floorActionAddsWhenSomeAreOut() {
        val leader = room("L", members = setOf("L", "a"))
        val a = room("a")
        val b = room("b")
        val bound = boundOf(listOf(leader, a, b))
        assertEquals(FloorAction.AddAll(listOf("b")), floorGroupAction(floor(leader, a, b), leader, bound))
    }

    @Test
    fun floorActionRemovesWhenAllAreIn() {
        val leader = room("L", members = setOf("L", "a", "b"), canGroup = listOf("a", "b"))
        val a = room("a")
        val b = room("b")
        val bound = boundOf(listOf(leader, a, b))
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
        val bound = boundOf(listOf(leader, a, b))
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

    // What the server's list would say about who follows whom, built from the fixtures' member lists.
    private fun boundOf(players: List<PlayerData>): Map<String, String> =
        players.flatMap { p -> p.player.groupMembers.orEmpty().filter { it != p.playerId }.map { it to p.playerId } }
            .toMap()

    private fun serverList(vararg sections: SpeakerSection, rooms: Map<String, SpeakerRoom> = emptyMap()) =
        SpeakerList(1, sections.toList(), rooms, emptyList())

    private val open = SpeakerRoom(available = true, groupLeader = null, groupWith = emptyList())

    @Test
    fun joinKeepsTheServersSectionsAndOrderAndSkipsRoomsWithoutLiveData() {
        val list = serverList(
            SpeakerSection("floor:up", SectionKind.FLOOR, "Upstairs", listOf("z", "ghost")),
            SpeakerSection("floor:main", SectionKind.FLOOR, "Main", listOf("b", "a")),
            SpeakerSection("other", SectionKind.OTHER, null, listOf("ghost")),
        )
        val sections = listSections(list, listOf(room("a"), room("b"), room("z"))) { _, _ -> true }
        assertEquals(listOf("floor:up", "floor:main"), sections.map { it.key })
        assertEquals(listOf("z"), sections[0].rooms.map { it.playerId })
        // The server's order stands: no re-sorting by name.
        assertEquals(listOf("b", "a"), sections[1].rooms.map { it.playerId })
    }

    @Test
    fun joinMarksOtherAndDropsRoomsTheFilterRefuses() {
        val list = serverList(
            SpeakerSection("other", SectionKind.OTHER, null, listOf("a", "phone")),
            rooms = mapOf("a" to open, "phone" to open.copy(available = false)),
        )
        val sections = listSections(list, listOf(room("a"), room("phone"))) { _, server -> server?.available == true }
        assertTrue(sections.single().isOther)
        assertEquals(listOf("a"), sections.single().rooms.map { it.playerId })
    }

    @Test
    fun joinPassesTheLivePlayerSoThePhoneCanBeLiftedOut() {
        val phone = room("p").copy(isLocal = true)
        val list = serverList(SpeakerSection("all", SectionKind.ALL, null, listOf("p", "a")))
        val sections = listSections(list, listOf(phone, room("a"))) { r, _ -> !r.isLocal }
        assertEquals(listOf("a"), sections.single().rooms.map { it.playerId })
    }

    @Test
    fun aStockServerGivesOnePlainListSortedByName() {
        val sections = listSections(null, listOf(room("1", "Zed"), room("2", "alpha"))) { _, _ -> true }
        assertEquals(1, sections.size)
        assertNull(sections.single().title)
        assertTrue(!sections.single().isOther)
        assertEquals(listOf("alpha", "Zed"), sections.single().rooms.map { it.player.name })
    }

    @Test
    fun aStockServerWithNoRoomsGivesNoSections() {
        assertEquals(emptyList(), listSections(null, emptyList()) { _, _ -> true })
    }
}
