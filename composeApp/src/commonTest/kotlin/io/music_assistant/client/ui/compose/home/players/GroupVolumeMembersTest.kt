package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.speakers.SpeakerGroup
import io.music_assistant.client.data.speakers.SpeakerList
import io.music_assistant.client.data.speakers.SpeakerRoom
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupVolumeMembersTest {
    private fun child(
        id: String,
        name: String,
        volume: Float? = 40f,
        bound: Boolean = true,
        accessible: Boolean = true,
    ) =
        PlayerDataFixtures.bind().copy(
            id = id,
            name = name,
            volume = volume,
            volumeSliderAccessible = accessible,
            isBound = bound,
        )

    private fun leader(type: PlayerType = PlayerType.PLAYER, children: List<PlayerData.ChildBind>) =
        PlayerDataFixtures.playerData(playerType = type, groupChildren = children)

    @Test
    fun aSyncLeaderIsListedWithItsOwnVolume() {
        val data = leader(children = listOf(child("b", "Bedroom")))

        val members = groupVolumeMembers(data, localPlayerId = null)

        assertEquals(listOf(data.player.name, "Bedroom").sortedBy { it.lowercase() }, members.map { it.name })
        assertEquals(true, members.single { it.id == data.player.id }.isLeader)
        assertEquals(50f, members.single { it.isLeader }.volume)
    }

    @Test
    fun aGroupTypePlayerIsNotListed() {
        val data = leader(type = PlayerType.GROUP, children = listOf(child("b", "Bedroom")))

        assertEquals(listOf("b"), groupVolumeMembers(data, null).map { it.id })
    }

    @Test
    fun unboundNoVolumeUnreachableAndLocalPlayersAreDropped() {
        val data = leader(
            type = PlayerType.GROUP,
            children = listOf(
                child("ok", "Kitchen"),
                child("unbound", "Unbound", bound = false),
                child("novol", "NoVolume", volume = null),
                child("locked", "Locked", accessible = false),
                child("phone", "Phone"),
            ),
        )

        assertEquals(listOf("ok"), groupVolumeMembers(data, localPlayerId = "phone").map { it.id })
    }

    @Test
    fun membersAreSortedByNameIgnoringCase() {
        val data = leader(
            type = PlayerType.GROUP,
            children = listOf(child("1", "zed"), child("2", "Ann"), child("3", "bob")),
        )

        assertEquals(listOf("Ann", "bob", "zed"), groupVolumeMembers(data, null).map { it.name })
    }

    private fun room(id: String, name: String, powered: Boolean = true, type: PlayerType = PlayerType.PLAYER) =
        PlayerDataFixtures.playerData(playerType = type).let {
            it.copy(
                player = it.player.copy(
                    id = id,
                    name = name,
                    groupVolume = 30f.takeIf { type == PlayerType.GROUP },
                    canPower = true,
                    isPowered = powered,
                ),
            )
        }

    private fun list(vararg groups: SpeakerGroup, names: Map<String, String> = emptyMap()) = SpeakerList(
        revision = 1,
        sections = emptyList(),
        rooms = names.mapValues { (_, n) ->
            SpeakerRoom(
                available = true,
                groupLeader = null,
                groupWith = emptyList(),
                name = n,
            )
        },
        groups = groups.toList(),
    )

    private val bath = room("bath", "Master bath")
    private val pair = room("pair", "Master Bedroom", type = PlayerType.GROUP)
    private val masterGroup = SpeakerGroup("bath", null, listOf("pair"))

    @Test
    fun roomsWithNoListFallBackToTheBoundChildren() {
        val data = leader(children = listOf(child("b", "Bedroom")))

        assertEquals(groupVolumeMembers(data, null), groupVolumeRooms(data, null, emptyList(), null))
    }

    @Test
    fun aRoomInNoGroupHasNoRows() {
        assertEquals(emptyList(), groupVolumeRooms(pair, list(), listOf(pair), null))
    }

    @Test
    fun aPairRoomIsARowDrivenByItsGroupVolume() {
        val rows = groupVolumeRooms(bath, list(masterGroup), listOf(bath, pair), null)

        assertEquals(listOf("Master bath", "Master Bedroom"), rows.map { it.name })
        assertEquals(true, rows.single { it.id == "pair" }.isGroupRoom)
        assertEquals(30f, rows.single { it.id == "pair" }.volume)
        assertEquals(false, rows.single { it.id == "bath" }.isGroupRoom)
        assertEquals(true, rows.single { it.id == "bath" }.isLeader)
    }

    @Test
    fun theSameRowsShowFromAMemberAndTheLeaderIsTheGroupLeader() {
        val all = listOf(bath, pair)
        val l = list(masterGroup)

        assertEquals(groupVolumeRooms(bath, l, all, null), groupVolumeRooms(pair, l, all, null))
        assertEquals("bath", groupVolumeLeader(pair, l, all).playerId)
    }

    @Test
    fun aPoweredOffRoomAndTheLocalPhoneAreLeftOut() {
        val off = room("pair", "Master Bedroom", powered = false, type = PlayerType.GROUP)
        val phone = room("phone", "Phone")
        val g = SpeakerGroup("bath", null, listOf("pair", "phone"))

        val rows = groupVolumeRooms(bath, list(g), listOf(bath, off, phone), localPlayerId = "phone")

        assertEquals(listOf("bath"), rows.map { it.id })
    }

    @Test
    fun theListsRoomNameWinsOverThePlayersName() {
        val named = list(masterGroup, names = mapOf("bath" to "Bathroom"))
        val rows = groupVolumeRooms(bath, named, listOf(bath, pair), null)

        assertEquals("Bathroom", rows.single { it.id == "bath" }.name)
    }
}
