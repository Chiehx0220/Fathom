package io.github.aedev.flow.ui.components.profiles

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.data.profile.ProfileAvatarPhotos
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.theme.profileTones
import java.text.BreakIterator

/**
 * A profile as a circle in its colour, showing its photo, its emoji or the first character of its name. Material 3
 * ships no avatar, so this is a Surface; the name beside it is what screen readers announce, so it adds no label.
 * [label] is the name to take the initial from while it is still being typed.
 */
@Composable
fun ProfileAvatar(
    profile: Profile,
    modifier: Modifier = Modifier,
    size: Dp = ProfileAvatarDefaults.Size,
    label: String? = null,
) {
    val context = LocalContext.current
    val initial = profileInitial(label ?: profile.displayName(context))
    val tones = profileTones(profile.colorKey)
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = tones.container,
        contentColor = tones.content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (profile.avatar.kind) {
                ProfileAvatar.PHOTO -> {
                    val request = remember(profile.id, profile.avatar.photoVersion) { profilePhotoRequest(context, profile) }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                ProfileAvatar.EMOJI -> {
                    Text(profile.avatar.emoji.ifBlank { initial }, style = textStyleFor(size))
                }

                else -> {
                    Text(initial, style = textStyleFor(size))
                }
            }
        }
    }
}

/** A profile's saved photo, cached under its version so a new photo is never hidden behind the old one. */
fun profilePhotoRequest(
    context: Context,
    profile: Profile,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(ProfileAvatarPhotos.fileFor(context, profile.id))
        .memoryCacheKey("profile_avatar:${profile.id}:${profile.avatar.photoVersion}")
        .build()

object ProfileAvatarDefaults {
    val Size = 40.dp
}

@Composable
private fun textStyleFor(size: Dp): TextStyle =
    when {
        size >= LargeAvatar -> MaterialTheme.typography.headlineMedium
        size >= ProfileAvatarDefaults.Size -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.labelLarge
    }

private val LargeAvatar = 64.dp

/** The first user-perceived character of [name], whole, so a flag or an accented letter is never cut in half. */
internal fun profileInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val breaks = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
    return trimmed.substring(0, breaks.next()).uppercase()
}
