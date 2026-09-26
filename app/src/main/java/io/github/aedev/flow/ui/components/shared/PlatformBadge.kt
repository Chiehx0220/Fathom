package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.ui.theme.BilibiliPink
import io.github.aedev.flow.ui.theme.YouTubeRed

private val PlatformBadgeHorizontalPadding = 5.dp
private val PlatformBadgeVerticalPadding = 2.dp
private val PlatformBadgeFontSize = 8.sp

/** The service's brand color, for anything (this badge, a future accent) that wants to color-code by source. */
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

/**
 * Small filled tag naming which service a video came from, color-coded by brand.
 */
@Composable
fun PlatformBadge(
    serviceId: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = serviceBrandColor(serviceId),
        contentColor = Color.White,
    ) {
        Text(
            text = serviceDisplayName(serviceId),
            style = MaterialTheme.typography.labelSmall,
            fontSize = PlatformBadgeFontSize,
            lineHeight = PlatformBadgeFontSize * 1.3f,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.padding(
                    horizontal = PlatformBadgeHorizontalPadding,
                    vertical = PlatformBadgeVerticalPadding,
                ),
        )
    }
}
