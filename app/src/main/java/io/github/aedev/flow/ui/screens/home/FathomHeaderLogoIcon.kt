package io.github.aedev.flow.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import io.github.aedev.flow.R
import io.github.aedev.flow.platform.LauncherIconArt
import io.github.aedev.flow.ui.components.LauncherIconImage

private const val INCOGNITO_SCALE = 0.78f

@Suppress("ktlint:standard:max-line-length")
private const val FLOW_INCOGNITO_GLYPH_PATH = "M17.06 13C15.2 13 13.64 14.33 13.24 16.1C12.29 15.69 11.42 15.8 10.76 16.09C10.35 14.31 8.79 13 6.94 13C4.77 13 3 14.79 3 17C3 19.21 4.77 21 6.94 21C9 21 10.68 19.38 10.84 17.32C11.18 17.08 12.07 16.63 13.16 17.34C13.34 19.39 15 21 17.06 21C19.23 21 21 19.21 21 17C21 14.79 19.23 13 17.06 13M6.94 19.86C5.38 19.86 4.13 18.58 4.13 17S5.39 14.14 6.94 14.14C8.5 14.14 9.75 15.42 9.75 17S8.5 19.86 6.94 19.86M17.06 19.86C15.5 19.86 14.25 18.58 14.25 17S15.5 14.14 17.06 14.14C18.62 14.14 19.88 15.42 19.88 17S18.61 19.86 17.06 19.86M22 10.5H2V12H22V10.5M15.53 2.63C15.31 2.14 14.75 1.88 14.22 2.05L12 2.79L9.77 2.05L9.72 2.04C9.19 1.89 8.63 2.17 8.43 2.68L6 9H18L15.56 2.68L15.53 2.63Z"

/** Launcher icon tile; cross-fades to the incognito glyph while Deep Flow is active. */
@Composable
internal fun FathomHeaderLogoIcon(
    isDeepFlowActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val tileColor = colorResource(R.color.ic_fathom_background)
    val glyphColor = colorResource(R.color.fathom_logo_ink)
    val glyphPath = remember { PathParser().parsePathString(FLOW_INCOGNITO_GLYPH_PATH).toPath() }

    val incognitoAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowIncognitoAlpha",
    )

    Box(modifier) {
        LauncherIconImage(Modifier.fillMaxSize().alpha(1f - incognitoAlpha))
        if (incognitoAlpha > 0f) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .alpha(incognitoAlpha)
                    .background(tileColor, RoundedCornerShape(LauncherIconArt.CORNER_PERCENT)),
            ) {
                val unit = size.width / 24f
                val inset = 12f * (1f - INCOGNITO_SCALE)
                withTransform({
                    scale(unit, unit, Offset.Zero)
                    translate(inset, inset)
                    scale(INCOGNITO_SCALE, INCOGNITO_SCALE, Offset.Zero)
                }) { drawPath(path = glyphPath, color = glyphColor) }
            }
        }
    }
}
