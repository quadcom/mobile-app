package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerType
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
}
