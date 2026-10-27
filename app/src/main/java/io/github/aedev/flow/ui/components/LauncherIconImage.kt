package io.github.aedev.flow.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.platform.LauncherIconArt

/** Active launcher alias, redrawn when the picker stores a new choice. */
@Composable
private fun rememberLauncherIcon(): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val stored by remember(context) { LauncherIconArt.entryPoint(context).playerPreferences().selectedAppIcon }
        .collectAsStateWithLifecycle(initialValue = null)
    return produceState(remember(context) { LauncherIconArt.cached(context)?.asImageBitmap() }, stored) {
        value = LauncherIconArt.renderActive(context).asImageBitmap()
    }.value
}

/** Device launcher icon mask. */
object LauncherIconShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ) = Outline.Generic(LauncherIconArt.mask(size.minDimension).asComposePath())
}

/** The active launcher icon in the device mask; a plain tile until the first draw. */
@Composable
fun LauncherIconImage(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val icon = rememberLauncherIcon()
    if (icon == null) {
        Box(modifier.background(colorResource(R.color.ic_fathom_background), LauncherIconShape))
    } else {
        Image(bitmap = icon, contentDescription = contentDescription, modifier = modifier, filterQuality = FilterQuality.High)
    }
}
