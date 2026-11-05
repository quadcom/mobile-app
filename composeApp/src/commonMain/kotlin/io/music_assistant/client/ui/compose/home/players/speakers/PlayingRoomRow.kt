// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.location.GroupColour
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.qualityTier
import io.music_assistant.client.imageloader.rememberArtworkRequest
import io.music_assistant.client.ui.compose.common.icons.NowPlayingIcon
import io.music_assistant.client.ui.compose.common.icons.PauseIcon
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.home.players.iconRes
import io.music_assistant.client.utils.formatDuration
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_nothing
import musicassistantclient.composeapp.generated.resources.speakers_active
import musicassistantclient.composeapp.generated.resources.speakers_group_with
import musicassistantclient.composeapp.generated.resources.speakers_pause_room
import musicassistantclient.composeapp.generated.resources.speakers_paused
import musicassistantclient.composeapp.generated.resources.speakers_play_room
import musicassistantclient.composeapp.generated.resources.speakers_playing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.DurationUnit

/** Cover art strength behind the text; the web uses the same value. */
private const val ART_ALPHA = 0.55f

/**
 * A room that is playing, or the active room. Tapping selects it; the link button opens the
 * group editor for this row's room without changing the active room, and the play button toggles
 * playback without selecting the row. Cover art of the current item sits behind the text.
 */
@Composable
internal fun PlayingRoomRow(
    item: PlayerData,
    active: Boolean,
    onSelect: () -> Unit,
    onOpenGroupEditor: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    groupColour: GroupColour? = null,
) {
    val colors = MaterialTheme.colorScheme
    val media = item.player.currentMedia
    val shape = CardShape
    val queueItem = item.queueInfo?.currentItem
    val duration = media?.duration?.takeIf { it > 0 }?.toFloat()
    val elapsed = item.queueInfo?.elapsedTime?.toFloat()
    val progress = if (duration != null && elapsed != null) (elapsed / duration).coerceIn(0f, 1f) else 0f
    val playing = item.player.isPlaying || item.player.isAnnouncing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .let { if (active) it.border(1.dp, colors.primary, shape) else it }
            .clickable(onClick = onSelect),
    ) {
        // Right angle at the corner; the row's clip trims it to the rounded shape.
        groupColour?.let { colour ->
            Canvas(Modifier.align(Alignment.TopEnd).size(30.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    },
                    Color(colour.argb),
                )
            }
        }
        val artUrl = media?.imageUrl
        if (artUrl != null) {
            AsyncImage(
                model = rememberArtworkRequest(artUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = ART_ALPHA,
                modifier = Modifier.matchParentSize(),
            )
            // Dark at the bottom, fading out going up, so the text stays readable over any cover.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
            )
        } else {
            Icon(
                painter = painterResource(item.iconRes()),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-8).dp)
                    .size(58.dp),
                tint = colors.onSurfaceVariant.copy(alpha = BACKGROUND_ICON_ALPHA),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 88.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Text(
                text = groupColour?.word() ?: item.player.name,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            if (media != null) {
                Text(
                    text = listOfNotNull(media.title, media.artist?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (playing) NowPlayingIcon(size = 12.dp)
                    val state = stringResource(
                        if (playing) Res.string.speakers_playing else Res.string.speakers_paused,
                    )
                    val time = elapsed?.takeIf { duration != null }?.let {
                        it.formatDuration(DurationUnit.SECONDS) + " / " +
                            duration?.formatDuration(DurationUnit.SECONDS)
                    }
                    val details = listOfNotNull(state, time, queueItem?.qualityTier?.name)
                        .joinToString(" · ")
                    Text(
                        text = details,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.players_nothing),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        // The phone cannot lead a group, so its row has no link button.
        val linkButton = !item.isLocal
        // Anything loaded can be played or paused; a spinner shows while the request is working.
        if (media != null) {
            // Names the room, so it differs from the player bar's own Play/Pause for screen readers and tests.
            val playLabel = stringResource(
                if (playing) Res.string.speakers_pause_room else Res.string.speakers_play_room,
                item.player.name,
            )
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = if (linkButton) 46.dp else 6.dp).size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onPlayPause, enabled = item.player.isSelectable) {
                    Icon(
                        imageVector = if (playing) PauseIcon else PlayIcon,
                        contentDescription = playLabel,
                        modifier = Modifier.size(30.dp),
                    )
                }
                if (item.playRequestPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        if (linkButton) {
            IconButton(
                onClick = onOpenGroupEditor,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = stringResource(
                        Res.string.speakers_group_with,
                        item.player.name,
                    ),
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        if (active) {
            Text(
                text = stringResource(Res.string.speakers_active),
                // Bottom right, above the progress bar: the colour triangle owns the top corner.
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 14.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.primary.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(colors.primary),
            )
        }
    }
}
