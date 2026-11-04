package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.MainDataSource.Companion.resolveSelectedPlayerId
import kotlin.test.Test
import kotlin.test.assertEquals

/** The swipe shows group leaders only, and picking a grouped room shows its group (PLAN_MMA_05). */
class PlayerPagesTest {
    private fun room(id: String, leader: String? = null, available: Boolean = true): PlayerData {
        val base = PlayerDataFixtures.playerData(name = id)
        return base.copy(
            player = base.player.copy(id = id, isAvailable = available, needsSetup = false),
            parentBind = leader?.let { PlayerData.ParentBind(it, it, isPlaying = true, isGroup = false) },
        )
    }

    @Test
    fun aGroupedRoomHasNoPageOfItsOwn() {
        val rooms = listOf(room("great"), room("bath", leader = "great"), room("office"))

        assertEquals(listOf("great", "office"), rooms.pagePlayers().map { it.playerId })
        assertEquals(mapOf("bath" to "great"), rooms.pageLeaders())
    }

    @Test
    fun aMemberKeepsItsPageWhenItsLeaderCannotBeShown() {
        val rooms = listOf(room("great", available = false), room("bath", leader = "great"))

        assertEquals(listOf("bath"), rooms.pagePlayers().map { it.playerId })
        assertEquals(emptyMap(), rooms.pageLeaders())
    }

    @Test
    fun pickingAGroupedRoomShowsItsLeader() {
        val rooms = listOf(room("office"), room("great"), room("bath", leader = "great"))

        val selected = resolveSelectedPlayerId(
            visiblePlayerIds = rooms.pagePlayers().map { it.playerId },
            userChoice = "bath",
            leaderOf = rooms.pageLeaders(),
        )

        assertEquals("great", selected)
    }

    @Test
    fun theRoomComesBackWhenItsGroupBreaksUp() {
        val rooms = listOf(room("office"), room("great"), room("bath"))

        val selected = resolveSelectedPlayerId(
            visiblePlayerIds = rooms.pagePlayers().map { it.playerId },
            userChoice = "bath",
            leaderOf = rooms.pageLeaders(),
        )

        assertEquals("bath", selected)
    }
}
