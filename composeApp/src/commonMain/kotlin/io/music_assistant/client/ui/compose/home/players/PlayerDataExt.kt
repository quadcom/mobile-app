package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.sharedicons.SharedIcons
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_current_player
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerData.contentDescription(): String {
    val currentPlayerContentDescription =
        stringResource(Res.string.cd_current_player, player.name)
    return currentPlayerContentDescription
}

fun PlayerData.label(): String {
    val boundCount = childrenBinds.count { it.isBound }
    val playerLabel = when {
        player.isGroup -> "${player.name} (${player.groupMembers?.size ?: 0})"
        boundCount > 0 -> "${player.name} + $boundCount"
        else -> player.name
    }
    return playerLabel
}

/** A room card passes `asGroup = false` so it keeps the room's own icon. */
fun PlayerData.iconRes(asGroup: Boolean = true): DrawableResource {
    val iconId = when {
        isLocal -> SharedIcons.SMARTPHONE
        asGroup && leadsRoomGroup -> SharedIcons.SPEAKERS
        else -> player.icon
    }
    return SharedIcons.getResource(iconId)
}
