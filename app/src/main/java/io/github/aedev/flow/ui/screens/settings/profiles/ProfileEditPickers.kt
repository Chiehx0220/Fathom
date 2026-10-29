package io.github.aedev.flow.ui.screens.settings.profiles

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.ui.components.profiles.ProfileAvatar
import io.github.aedev.flow.ui.components.shared.FlowColorSwatch
import io.github.aedev.flow.ui.components.shared.FlowColorSwatchDefaults
import io.github.aedev.flow.ui.theme.ProfileColors
import io.github.aedev.flow.ui.theme.profileTones

private val PickerSpacing = 10.dp

private val ColorNames =
    mapOf(
        "blue" to R.string.profiles_color_blue,
        "teal" to R.string.profiles_color_teal,
        "green" to R.string.profiles_color_green,
        "amber" to R.string.profiles_color_amber,
        "orange" to R.string.profiles_color_orange,
        "red" to R.string.profiles_color_red,
        "pink" to R.string.profiles_color_pink,
        "purple" to R.string.profiles_color_purple,
    )

/** The avatar being edited; a photo picked but not yet saved is shown straight from the picker. */
@Composable
internal fun ProfilePreview(
    profile: Profile,
    label: String,
    pendingPhoto: Uri?,
    size: Dp,
) {
    if (pendingPhoto == null) {
        ProfileAvatar(profile, size = size, label = label)
        return
    }
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = profileTones(profile.colorKey).container) {
        AsyncImage(model = pendingPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** The profile colours, each drawn in the tone the avatar will wear. */
@Composable
internal fun ProfileColorPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PickerSpacing),
        verticalArrangement = Arrangement.spacedBy(PickerSpacing),
    ) {
        ProfileColors.seeds.forEach { (key, _) ->
            val name = ColorNames[key]?.let { stringResource(it) } ?: key
            FlowColorSwatch(
                color = profileTones(key).container,
                selected = key == selected,
                onClick = { onSelect(key) },
                modifier = Modifier.semantics { contentDescription = name },
            )
        }
    }
}

/**
 * A photo, the initial, or one of a few emoji. The photo cell is left out where no picker can open, as on a TV
 * without one.
 */
@Composable
internal fun ProfilePicturePicker(
    kind: String,
    emoji: String,
    photo: Any?,
    canPickPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onInitial: () -> Unit,
    onEmoji: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PickerSpacing),
        verticalArrangement = Arrangement.spacedBy(PickerSpacing),
    ) {
        if (canPickPhoto) {
            val label = stringResource(R.string.profiles_photo)
            PictureCell(selected = kind == ProfileAvatar.PHOTO, label = label, onClick = onPickPhoto) {
                if (photo != null && kind == ProfileAvatar.PHOTO) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(FlowColorSwatchDefaults.IconSize),
                    )
                }
            }
        }
        PictureCell(selected = kind == ProfileAvatar.INITIAL, label = stringResource(R.string.profiles_initial), onClick = onInitial) {
            Text(stringResource(R.string.profiles_initial_glyph), style = MaterialTheme.typography.titleMedium)
        }
        stringArrayResource(R.array.profiles_emoji).forEach { option ->
            PictureCell(selected = kind == ProfileAvatar.EMOJI && emoji == option, label = option, onClick = { onEmoji(option) }) {
                Text(option, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PictureCell(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .size(FlowColorSwatchDefaults.Size)
                .clip(CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
