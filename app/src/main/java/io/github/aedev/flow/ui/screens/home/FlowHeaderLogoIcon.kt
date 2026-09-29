package io.github.aedev.flow.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.cos

// The Fathom badge, its water and ruler mark, in a 24-unit box; the launcher icon's drawing in one colour.
private const val FLOW_LOGO_BG_PATH = "M7,2 L17,2 C20,2 22,4 22,7 L22,17 C22,20 20,22 17,22 L7,22 C4,22 2,20 2,17 L2,7 C2,4 4,2 7,2 Z"
private const val FLOW_LOGO_RULER_PATH = "M8.88,4.32 L8.88,18.72 M8.88,5.28 L18.24,5.28 M8.88,10.08 L12,10.08 M8.88,14.88 L12,14.88"
private const val FLOW_LOGO_TRIANGLE_PATH = "M16.44,12.96 L12.36,10.56 L12.36,15.36 Z"

private const val WAVE_LEFT = 2f
private const val WAVE_RIGHT = 22f
private const val WAVE_STEP = 0.5f
private const val WAVE_MID_Y = 13.4f
private const val WAVE_AMPLITUDE = 0.64f
private const val WAVE_PERIOD = 6f
private const val WAVE_BOTTOM = 24f
private const val WAVE_FILL_ALPHA = 0.3f
private const val WAVE_LINE_ALPHA = 0.85f

/** The water across the badge: the crest line, and the water below it closed off at the bottom. */
private fun wavePaths(): Pair<Path, Path> {
    val line = Path()
    var x = WAVE_LEFT
    while (x <= WAVE_RIGHT) {
        val y = WAVE_MID_Y + WAVE_AMPLITUDE * cos(2.0 * PI * (x - 1f) / WAVE_PERIOD).toFloat()
        if (x == WAVE_LEFT) line.moveTo(x, y) else line.lineTo(x, y)
        x += WAVE_STEP
    }
    val fill = Path().apply { addPath(line) }
    fill.lineTo(WAVE_RIGHT, WAVE_BOTTOM)
    fill.lineTo(WAVE_LEFT, WAVE_BOTTOM)
    fill.close()
    return line to fill
}

@Suppress("ktlint:standard:max-line-length")
private const val FLOW_INCOGNITO_GLYPH_PATH = "M17.06 13C15.2 13 13.64 14.33 13.24 16.1C12.29 15.69 11.42 15.8 10.76 16.09C10.35 14.31 8.79 13 6.94 13C4.77 13 3 14.79 3 17C3 19.21 4.77 21 6.94 21C9 21 10.68 19.38 10.84 17.32C11.18 17.08 12.07 16.63 13.16 17.34C13.34 19.39 15 21 17.06 21C19.23 21 21 19.21 21 17C21 14.79 19.23 13 17.06 13M6.94 19.86C5.38 19.86 4.13 18.58 4.13 17S5.39 14.14 6.94 14.14C8.5 14.14 9.75 15.42 9.75 17S8.5 19.86 6.94 19.86M17.06 19.86C15.5 19.86 14.25 18.58 14.25 17S15.5 14.14 17.06 14.14C18.62 14.14 19.88 15.42 19.88 17S18.61 19.86 17.06 19.86M22 10.5H2V12H22V10.5M15.53 2.63C15.31 2.14 14.75 1.88 14.22 2.05L12 2.79L9.77 2.05L9.72 2.04C9.19 1.89 8.63 2.17 8.43 2.68L6 9H18L15.56 2.68L15.53 2.63Z"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlowHeaderLogoIcon(
    isDeepFlowActive: Boolean,
    onToggleDeepFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val haptic = LocalHapticFeedback.current

    val bgPath =
        remember {
            PathParser().parsePathString(FLOW_LOGO_BG_PATH).toPath()
        }
    val rulerPath =
        remember {
            PathParser().parsePathString(FLOW_LOGO_RULER_PATH).toPath()
        }
    val trianglePath =
        remember {
            PathParser().parsePathString(FLOW_LOGO_TRIANGLE_PATH).toPath()
        }
    val wave = remember { wavePaths() }
    val incognitoPath =
        remember {
            PathParser().parsePathString(FLOW_INCOGNITO_GLYPH_PATH).toPath()
        }

    val glyphAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowGlyphAlpha",
    )
    val incognitoAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowIncognitoAlpha",
    )

    Canvas(
        modifier =
            modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDeepFlow()
                },
            ),
    ) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        drawContext.canvas.save()
        drawContext.canvas.scale(sx, sy)
        drawPath(path = bgPath, color = primaryColor)

        if (glyphAlpha > 0f) {
            clipPath(bgPath) {
                drawPath(path = wave.second, color = onPrimaryColor.copy(alpha = WAVE_FILL_ALPHA * glyphAlpha))
                drawPath(
                    path = wave.first,
                    color = onPrimaryColor.copy(alpha = WAVE_LINE_ALPHA * glyphAlpha),
                    style = Stroke(width = 0.9f, cap = StrokeCap.Round),
                )
            }
            drawContext.canvas.save()
            drawContext.canvas.translate(-1.56f, 0.48f)
            drawPath(
                path = rulerPath,
                color = onPrimaryColor.copy(alpha = glyphAlpha),
                style = Stroke(width = 1.2f, cap = StrokeCap.Round),
            )
            drawContext.canvas.restore()
            drawPath(path = trianglePath, color = onPrimaryColor.copy(alpha = glyphAlpha))
        }
        if (incognitoAlpha > 0f) {
            val incognitoScale = 0.65f
            val incognitoOffset = 12f * (1f - incognitoScale)
            drawContext.canvas.save()
            drawContext.canvas.translate(incognitoOffset, incognitoOffset)
            drawContext.canvas.scale(incognitoScale, incognitoScale)
            drawPath(path = incognitoPath, color = onPrimaryColor.copy(alpha = incognitoAlpha))
            drawContext.canvas.restore()
        }

        drawContext.canvas.restore()
    }
}
