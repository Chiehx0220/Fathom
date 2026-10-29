package io.github.aedev.flow.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The photos profiles use as avatars: one square WebP per profile, named by its id. The registry only records that a
 * profile has one and when it changed, and that version is what tells a cached image apart from a new photo.
 */
@Singleton
class ProfileAvatarPhotos
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun file(id: String): File = fileFor(context, id)

        /** Saves the picked image as [id]'s photo and returns its version, or null when it can't be read. */
        suspend fun save(
            id: String,
            source: Uri,
        ): Long? =
            withContext(Dispatchers.IO) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(source)
                        .size(PHOTO_PX)
                        .scale(Scale.FILL)
                        .allowHardware(false)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return@withContext null
                write(file(id), centreSquare(image.toBitmap(), PHOTO_PX))
                System.currentTimeMillis()
            }

        fun delete(id: String): Boolean = file(id).delete()

        private fun write(
            target: File,
            bitmap: Bitmap,
        ) {
            target.parentFile?.mkdirs()
            val file = AtomicFile(target)
            val out = file.startWrite()
            try {
                bitmap.compress(webp(), PHOTO_QUALITY, out)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }

        @Suppress("DEPRECATION")
        private fun webp(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

        companion object {
            const val DIR = "profile_avatars"
            private const val PHOTO_PX = 256
            private const val PHOTO_QUALITY = 90

            fun fileFor(
                context: Context,
                id: String,
            ): File = fileIn(context.filesDir, id)

            internal fun fileIn(
                filesDir: File,
                id: String,
            ): File {
                require(isSafeProfileId(id)) { "Unsafe profile id" }
                return File(filesDir, "$DIR/$id.webp")
            }
        }
    }

/** The middle square of [source], scaled to [side] pixels. */
private fun centreSquare(
    source: Bitmap,
    side: Int,
): Bitmap {
    val edge = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - edge) / 2, (source.height - edge) / 2, edge, edge)
    return if (edge == side) square else Bitmap.createScaledBitmap(square, side, side, true)
}
