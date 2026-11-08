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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.speakers.GroupColour
import io.music_assistant.client.ui.compose.home.players.iconRes
import org.jetbrains.compose.resources.painterResource

/** Primary at 20% over surfaceContainerLow, flattened so the fill is solid. */
internal val ActiveFill = Color(0xFF183C4A)

internal val CardShape = RoundedCornerShape(12.dp)

private const val FADE_MS = 200

// Faint enough that a name drawn over the icon stays readable on a bright phone screen.
internal const val BACKGROUND_ICON_ALPHA = 0.08f

/**
 * A 78dp square room card. [highlighted] is the group editor's "in this group" look (fill, border
 * and check fade in place); [dimmed] is a room bound into a group elsewhere. [tag] sits on the top
 * edge; null hides it. [groupColour] draws a 3dp line in that colour along the top edge. A null [onClick] makes
 * the card inert.
 */
@Composable
internal fun RoomCard(
    player: PlayerData,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    tag: String? = null,
    tagAlpha: Float = 0.4f,
    groupColour: GroupColour? = null,
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
            tint = colors.onSurfaceVariant.copy(alpha = BACKGROUND_ICON_ALPHA),
        )
        // The tag gets its own strip at the top and the name the space below it, so the two never
        // overlap. Both shrink to fit instead of wrapping mid-word: with a large system font
        // (measured 1.15 on Adrian's phone) a 14sp "Greatroom" is wider than the card.
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tag != null) {
                BasicText(
                    text = tag,
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface.copy(alpha = tagAlpha),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText(
                    text = shownName,
                    modifier = Modifier
                        .alpha(contentAlpha)
                        .semantics { contentDescription = fullName },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 1.2.em,
                    ),
                    // One line per word: a word that would wrap overflows instead, so the size steps down.
                    maxLines = shownName.count { it == '\n' } + 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                )
            }
        }
        // Inside the clipped box, so the line follows the card's rounded corners.
        groupColour?.let {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(Color(it.argb)),
            )
        }
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
