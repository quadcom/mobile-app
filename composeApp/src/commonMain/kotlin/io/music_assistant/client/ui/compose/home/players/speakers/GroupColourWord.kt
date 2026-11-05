package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.speakers.GroupColour
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.speakers_colour_green
import musicassistantclient.composeapp.generated.resources.speakers_colour_orange
import musicassistantclient.composeapp.generated.resources.speakers_colour_pink
import musicassistantclient.composeapp.generated.resources.speakers_colour_purple
import musicassistantclient.composeapp.generated.resources.speakers_colour_teal
import musicassistantclient.composeapp.generated.resources.speakers_colour_yellow
import org.jetbrains.compose.resources.stringResource

/** The colour's name, shown beside the colour so a colour-blind user can tell groups apart. */
@Composable
internal fun GroupColour.word(): String = stringResource(
    when (this) {
        GroupColour.ORANGE -> Res.string.speakers_colour_orange
        GroupColour.PURPLE -> Res.string.speakers_colour_purple
        GroupColour.GREEN -> Res.string.speakers_colour_green
        GroupColour.PINK -> Res.string.speakers_colour_pink
        GroupColour.YELLOW -> Res.string.speakers_colour_yellow
        GroupColour.TEAL -> Res.string.speakers_colour_teal
    },
)
