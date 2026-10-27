package io.github.aedev.flow.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import java.io.File
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LauncherIconEntryPoint {
    fun appIconController(): AppIconController

    fun playerPreferences(): PlayerPreferences
}

/** Renders a launcher alias clipped to the device icon mask, as the launcher draws it. */
object LauncherIconArt {
    const val TILE_PX = 192
    private const val MASK_UNITS = 100

    // Adaptive canvas 108dp, mask-visible area 72dp.
    private const val CANVAS_DP = 108f
    private const val VISIBLE_DP = 72f

    private const val CACHE_NAME = "launcher_icon_mask"

    /** Last bitmap from [renderActive]; seeds composables so they skip the empty first frame. */
    @Volatile
    var lastActive: Bitmap? = null
        private set

    @Volatile
    private var lastSuffix: String? = null

    private fun cacheFile(
        context: Context,
        extension: String,
    ) = File(context.applicationContext.cacheDir, "$CACHE_NAME.$extension")

    /** [lastActive], else the persisted tile; null on first run. */
    fun cached(context: Context): Bitmap? {
        lastActive?.let { return it }
        val bitmap = runCatching { BitmapFactory.decodeFile(cacheFile(context, "png").path) }.getOrNull() ?: return null
        lastSuffix = runCatching { cacheFile(context, "suffix").readText() }.getOrNull()
        lastActive = bitmap
        return bitmap
    }

    fun entryPoint(context: Context): LauncherIconEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, LauncherIconEntryPoint::class.java)

    suspend fun renderActive(
        context: Context,
        sizePx: Int = TILE_PX,
    ): Bitmap {
        val suffix = entryPoint(context).appIconController().activeSuffix()
        lastActive?.takeIf { sizePx == TILE_PX && suffix == lastSuffix }?.let { return it }
        return withContext(Dispatchers.Default) {
            render(context, AppIcons.icon(suffix), sizePx).also { if (sizePx == TILE_PX) persist(context, it, suffix) }
        }.also {
            lastActive = it
            lastSuffix = suffix
        }
    }

    private fun persist(
        context: Context,
        bitmap: Bitmap,
        suffix: String,
    ) {
        runCatching {
            cacheFile(context, "png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cacheFile(context, "suffix").writeText(suffix)
        }
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
        val outside = mask(sizePx.toFloat()).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        return bitmap
    }

    /** Device icon mask scaled to [size]. */
    fun mask(size: Float): Path {
        val system = AdaptiveIconDrawable(null, null).apply { setBounds(0, 0, MASK_UNITS, MASK_UNITS) }.iconMask
        return Path(system).apply { transform(Matrix().apply { setScale(size / MASK_UNITS, size / MASK_UNITS) }) }
    }
}
