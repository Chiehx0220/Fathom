package io.github.aedev.flow.ui.components

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.aedev.flow.R

private const val SPLASH_ICON_NAMESPACE = "io.github.aedev.flow"

// Launcher-icon aliases, first one being the default. The splash draws the logo of whichever is enabled.
private val SPLASH_ICON_SUFFIXES = listOf(
    ".IconFlowRed",
    ".IconFlowLight",
    ".IconFlowPlay",
    ".IconAmoled",
    ".IconMonochrome",
    ".IconGhost",
    ".IconDynamic",
    ".IconMaterialSky",
    ".IconMaterialMint",
)

@Composable
fun FlowSplashScreen(
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onBackground

    // Detect the currently active app icon
    val activeSuffix = remember {
        val pm = context.packageManager
        val pkg = context.packageName
        SPLASH_ICON_SUFFIXES.firstOrNull { suffix ->
            val cn = ComponentName(pkg, "$SPLASH_ICON_NAMESPACE$suffix")
            pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: SPLASH_ICON_SUFFIXES.first()
    }
    // --- Animation States ---
    val scale = remember { Animatable(0f) }      // For the Logo Pop
    val waterProgress = remember { Animatable(0f) } // Water level in the logo, 0 to 1
    val alpha = remember { Animatable(1f) }      // For the Screen Fade Out
    
    // --- The Choreography ---
    LaunchedEffect(key1 = true) {
        // 1. Logo Springs In (0ms -> 600ms)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // 2. The water rises (wait 200ms, then fill)
        launch {
            delay(200)
            waterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
        }

        // 3. Wait for app to be ready, then Fade Out
        delay(1700)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 500)
        )
        
        // 4. Tell MainActivity to remove the Splash
        onAnimationFinished()
    }

    // --- The UI ---
    // Only render if we are visible
    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .alpha(alpha.value), // Controls the fade out
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. The logo: water rises and the play mark floats up to its place
                SplashLogoWave(
                    style = splashLogoStyle(activeSuffix, colorScheme),
                    logoScale = scale.value,
                    progress = waterProgress.value,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 2. The Text
                Text(
                    text = stringResource(R.string.app_name),
                    color = textColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.alpha(scale.value)
                )
            }
        }
    }
}
