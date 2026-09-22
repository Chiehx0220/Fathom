package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/** How one launcher-icon variant is drawn on the splash: optional badge, ruler, play mark and water colours. */
internal data class SplashLogoStyle(
    val badge: Color?,
    val ruler: Color,
    val mark: Color,
    val water: Color,
    val outlineMark: Boolean = false,
)

/** Variants without a badge draw straight onto the splash background, so their lines follow its contrast. */
internal fun splashLogoStyle(
    componentSuffix: String,
    colors: ColorScheme,
): SplashLogoStyle {
    val navy = Color(0xFF0F1D33)
    val cream = Color(0xFFF2EDE0)
    val gold = Color(0xFFC99A34)
    val onDark = colors.background.luminance() < 0.5f
    // The same blue as the launcher icon's water, so the splash shows what the icon shows.
    val blue = Color(0xFF5B8DEF)
    return when (componentSuffix) {
        ".IconFlowLight" -> SplashLogoStyle(Color.White, navy, gold, blue)
        ".IconFlowPlay" -> SplashLogoStyle(navy, cream, gold, Color(0xFF7FA8FF))
        ".IconAmoled" -> SplashLogoStyle(null, if (onDark) cream else navy, gold, blue)
        ".IconMonochrome" -> SplashLogoStyle(Color.White, Color(0xFF1C1B1F), Color(0xFF1C1B1F), Color(0xFF1C1B1F))
        ".IconGhost" -> SplashLogoStyle(null, colors.onBackground, colors.onBackground, colors.onBackground, outlineMark = true)
        ".IconDynamic" -> SplashLogoStyle(colors.secondaryContainer, colors.onSecondaryContainer, colors.onSecondaryContainer, colors.primary)
        else -> SplashLogoStyle(cream, navy, gold, blue)
    }
}

private const val BADGE_PATH = "M7,2 L17,2 C20,2 22,4 22,7 L22,17 C22,20 20,22 17,22 L7,22 C4,22 2,20 2,17 L2,7 C2,4 4,2 7,2 Z"
private const val RULER_PATH = "M8.88,4.32 L8.88,18.72 M8.88,5.28 L18.24,5.28 M8.88,10.08 L12,10.08 M8.88,14.88 L12,14.88"

// The logo's play mark in the logo's 24-unit box: its home beside the ruler, and its start at the bottom edge.
private const val MARK_HOME_X = 13.72f
private const val MARK_HOME_Y = 12.96f
private const val MARK_START_Y = 19.5f

// Where the water ends up, as in the launcher icon: just under the mark, which floats on it.
private const val WATER_FINAL_Y = 13.4f
private const val RULER_SHIFT_X = -1.56f
private const val RULER_SHIFT_Y = 0.48f

/**
 * The logo as a depth gauge. It starts as a bare ruler with the play mark at the bottom edge. Water rises
 * through the badge as [progress] grows and the mark floats on its surface, up beside the ruler to its place
 * at the middle tick, which completes the F. The water stays, as in the launcher icon.
 */
@Composable
internal fun SplashLogoWave(
    style: SplashLogoStyle,
    logoScale: Float,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    val badgePath = remember { PathParser().parsePathString(BADGE_PATH).toPath() }
    val rulerPath = remember { PathParser().parsePathString(RULER_PATH).toPath() }

    Canvas(modifier = modifier.size(90.dp)) {
        val unit = size.width / 24f
        val p = progress.coerceIn(0f, 1f)
        val level = MARK_START_Y + (WATER_FINAL_Y - MARK_START_Y) * p
        val amplitude = 0.45f
        val k = (2 * PI / 6.0).toFloat()

        fun surfaceY(x: Float) = level + amplitude * sin(k * x - phase)

        withTransform({
            scale(logoScale, logoScale, pivot = Offset(size.width / 2f, size.height / 2f))
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            style.badge?.let { drawPath(badgePath, it) }

            clipPath(badgePath) {
                val crest =
                    Path().apply {
                        moveTo(0f, surfaceY(0f))
                        var x = 0.5f
                        while (x <= 24f) {
                            lineTo(x, surfaceY(x))
                            x += 0.5f
                        }
                    }
                val water =
                    Path().apply {
                        addPath(crest)
                        lineTo(24f, 24f)
                        lineTo(0f, 24f)
                        close()
                    }
                drawPath(water, style.water.copy(alpha = 0.35f))
                drawPath(crest, style.water.copy(alpha = 0.85f), style = Stroke(width = 0.9f, cap = StrokeCap.Round))
            }

            withTransform({ translate(RULER_SHIFT_X, RULER_SHIFT_Y) }) {
                drawPath(rulerPath, style.ruler, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            }

            val floating = 1f - p
            val markY = level - (WATER_FINAL_Y - MARK_HOME_Y) * p + 0.25f * sin(phase * 2f) * floating
            val tilt = Math.toDegrees(atan(amplitude * k * cos(k * MARK_HOME_X - phase)).toDouble()).toFloat() * floating
            val mark =
                Path().apply {
                    moveTo(MARK_HOME_X + 2.72f, markY)
                    lineTo(MARK_HOME_X - 1.36f, markY - 2.4f)
                    lineTo(MARK_HOME_X - 1.36f, markY + 2.4f)
                    close()
                }
            rotate(degrees = tilt, pivot = Offset(MARK_HOME_X, markY)) {
                if (style.outlineMark) {
                    drawPath(mark, style.mark, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
                } else {
                    drawPath(mark, style.mark)
                }
            }
        }
    }
}
