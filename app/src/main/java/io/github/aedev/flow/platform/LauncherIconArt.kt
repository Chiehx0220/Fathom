package io.github.aedev.flow.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.util.AppIcons
import io.github.aedev.flow.util.LauncherIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LauncherIconEntryPoint {
    fun appIconController(): AppIconController

    fun playerPreferences(): PlayerPreferences
}

/** Renders a launcher alias as a rounded tile. */
object LauncherIconArt {
    const val TILE_PX = 192
    const val CORNER_PERCENT = 22

    // Adaptive canvas 108dp, mask-visible area 72dp.
    private const val CANVAS_DP = 108f
    private const val VISIBLE_DP = 72f

    /** Last bitmap from [renderActive]; seeds composables so they skip the empty first frame. */
    @Volatile
    var lastActive: Bitmap? = null
        private set

    fun entryPoint(context: Context): LauncherIconEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, LauncherIconEntryPoint::class.java)

    suspend fun renderActive(
        context: Context,
        sizePx: Int = TILE_PX,
    ): Bitmap {
        val suffix = entryPoint(context).appIconController().activeSuffix()
        return withContext(Dispatchers.Default) { render(context, AppIcons.icon(suffix), sizePx) }.also { lastActive = it }
    }

    fun render(
        context: Context,
        icon: LauncherIcon,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = checkNotNull(ContextCompat.getDrawable(context, icon.iconRes)) { "Missing launcher icon ${icon.suffix}" }
        val adaptive = drawable as? AdaptiveIconDrawable
        val layers = adaptive?.let { listOfNotNull(it.background, it.foreground) } ?: listOf(drawable)
        val full = if (adaptive != null) (sizePx * CANVAS_DP / VISIBLE_DP).roundToInt() else sizePx
        val offset = (sizePx - full) / 2
        layers.forEach { layer ->
            layer.setBounds(offset, offset, offset + full, offset + full)
            layer.draw(canvas)
        }
        // CLEAR over the inverse fill; a DST_IN mask leaves the pixels outside its shape untouched.
        val radius = sizePx * CORNER_PERCENT / 100f
        val outside =
            Path().apply {
                addRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, Path.Direction.CW)
                fillType = Path.FillType.INVERSE_WINDING
            }
        canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        return bitmap
    }
}
