package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** The group editor picks the command by flag; the arguments stay identical on both servers. */
class SetMembersRequestTest {
    @Test
    fun stockCommandIsTheDefault() {
        val request = Request.Player.setGroupMembers("L", listOf("a"), null)

        assertEquals("players/cmd/set_members", request.command)
        assertEquals(JsonPrimitive("L"), request.args?.get("target_player"))
        assertEquals(setOf("target_player", "player_ids_to_add"), request.args?.keys)
    }

    @Test
    fun speakerListCommandCarriesTheSameArguments() {
        val stock = Request.Player.setGroupMembers("L", listOf("a"), listOf("b"))
        val viaList = Request.Player.setGroupMembers(
            "L",
            listOf("a"),
            listOf("b"),
            command = APICommands.SPEAKER_LIST_SET_MEMBERS,
        )

        assertEquals("hass/speaker_list/set_members", viaList.command)
        assertEquals(stock.args, viaList.args)
    }
}
