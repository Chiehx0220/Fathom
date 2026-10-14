package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.ui.theme.BilibiliPink
import io.github.aedev.flow.ui.theme.YouTubeRed

private val PlatformBadgeDotSize = 5.dp
private val PlatformBadgeFontSize = 9.sp

/** Whether cards name their service. A feed that already shows one service turns it off. */
val LocalShowPlatformBadge = compositionLocalOf { true }

@Composable
fun ProvidePlatformBadge(
    show: Boolean,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalShowPlatformBadge provides show, content = content)

/** The service's brand color, for anything that wants to color-code by source. */
fun serviceBrandColor(serviceId: Int): Color =
    when (serviceId) {
        BILIBILI_SERVICE_ID -> BilibiliPink
        else -> YouTubeRed
    }

private fun serviceDisplayName(serviceId: Int): String =
    when (serviceId) {
        BILIBILI_SERVICE_ID -> "Bilibili"
        else -> "YouTube"
    }

/** A neutral pill with the service's brand color as a dot, so the label stays readable on any theme. */
@Composable
fun PlatformBadge(
    serviceId: Int,
    modifier: Modifier = Modifier,
) {
    if (!LocalShowPlatformBadge.current) return
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Box(
                Modifier
                    .size(PlatformBadgeDotSize)
                    .background(serviceBrandColor(serviceId), CircleShape),
            )
            Text(
                text = serviceDisplayName(serviceId),
                style = MaterialTheme.typography.labelSmall,
                fontSize = PlatformBadgeFontSize,
                lineHeight = PlatformBadgeFontSize * 1.3f,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A channel avatar with its service's badge beneath it. [modifier] applies to the avatar. */
@Composable
fun ChannelAvatarWithBadge(
    urls: List<String>,
    contentDescription: String?,
    avatarSize: Dp,
    serviceId: Int,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelAvatarStack(
            urls = urls,
            contentDescription = contentDescription,
            avatarSize = avatarSize,
            modifier = modifier,
        )
        // Reports the avatar's width, so a badge wider than it overhangs both sides instead of pushing the title over.
        val columnWidth = maxOf(avatarSize, LocalMinimumInteractiveComponentSize.current)
        PlatformBadge(
            serviceId = serviceId,
            modifier =
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints(maxWidth = Constraints.Infinity))
                    val width = columnWidth.roundToPx()
                    layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
                },
        )
    }
}
