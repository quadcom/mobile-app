// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players.speakers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.home.players.iconRes
import org.jetbrains.compose.resources.painterResource

/** Primary at 20% over surfaceContainerLow, flattened so the fill is solid. */
internal val ActiveFill = Color(0xFF183C4A)

internal val CardShape = RoundedCornerShape(12.dp)

private const val FADE_MS = 200

/**
 * A 78dp square room card. [highlighted] is the group editor's "in this group" look (fill, border
 * and check fade in place); [dimmed] is a room bound into a group elsewhere. [tag] sits on the top
 * edge; null hides it. A null [onClick] makes the card inert.
 */
@Composable
internal fun RoomCard(
    player: PlayerData,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    tag: String? = null,
    tagAlpha: Float = 0.4f,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (highlighted) ActiveFill else colors.surfaceContainerHigh,
        tween(FADE_MS),
    )
    val border by animateColorAsState(
        when {
            highlighted -> colors.primary
            dimmed -> colors.outlineVariant.copy(alpha = 0.5f)
            else -> colors.outlineVariant
        },
        tween(FADE_MS),
    )
    val checkAlpha by animateFloatAsState(if (highlighted) 1f else 0f, tween(FADE_MS))
    val contentAlpha by animateFloatAsState(if (dimmed) 0.4f else 1f, tween(250))
    val fullName = player.player.name
    // Names break at every space, so "Dining room" is always two lines.
    val shownName = fullName.replace(' ', '\n')
    val nameShift = when {
        dimmed -> 6.dp
        tag != null -> 5.dp
        else -> 0.dp
    }

    Box(
        modifier = modifier
            .size(78.dp)
            .clip(CardShape)
            .background(fill)
            .border(BorderStroke(1.dp, border), CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(player.iconRes()),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-10).dp, y = (-8).dp)
                .size(58.dp)
                .alpha(contentAlpha),
            tint = colors.onSurfaceVariant.copy(alpha = 0.12f),
        )
        if (tag != null) {
            Text(
                text = tag,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface.copy(alpha = tagAlpha),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = shownName,
            modifier = Modifier
                .offset(y = nameShift)
                .padding(horizontal = 2.dp)
                .alpha(contentAlpha)
                .semantics { contentDescription = fullName },
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(16.dp)
                .alpha(checkAlpha),
            tint = colors.primary,
        )
    }
}

/** Fixed 78dp cells, 6dp apart, wrapping to the sheet's width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
