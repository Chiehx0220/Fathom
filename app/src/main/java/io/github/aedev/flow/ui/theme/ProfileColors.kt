package io.github.aedev.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours a profile can wear. A profile stores the key, never the colour, so the palette can be retuned without
 * touching the registry. These are fixed seeds the user picks between, not theme roles; the tones actually drawn come
 * from [profileTones], in step with light and dark.
 */
object ProfileColors {
    val seeds: List<Pair<String, Color>> =
        listOf(
            "blue" to Color(0xFF3B6FD8),
            "teal" to Color(0xFF00897B),
            "green" to Color(0xFF43A047),
            "amber" to Color(0xFFF2A900),
            "orange" to Color(0xFFEF6C00),
            "red" to Color(0xFFD93F3F),
            "pink" to Color(0xFFD6457F),
            "purple" to Color(0xFF7E57C2),
        )

    fun seed(key: String): Color? = seeds.firstOrNull { it.first == key }?.second
}

/** The fill and the content colour a profile's avatar uses. */
@Immutable
data class ProfileTones(
    val container: Color,
    val content: Color,
)

/**
 * A profile colour as Material's container tones: light containers in a light scheme, dark in a dark one. A profile
 * with no colour, or one this build doesn't know, wears the scheme's own primary container.
 */
@Composable
fun profileTones(colorKey: String): ProfileTones {
    val scheme = MaterialTheme.colorScheme
    val seed = ProfileColors.seed(colorKey) ?: return ProfileTones(scheme.primaryContainer, scheme.onPrimaryContainer)
    val dark = scheme.surface.tone() < MID_TONE
    return if (dark) {
        ProfileTones(container = seed.withTone(DARK_CONTAINER_TONE), content = seed.withTone(DARK_CONTENT_TONE))
    } else {
        ProfileTones(container = seed.withTone(LIGHT_CONTAINER_TONE), content = seed.withTone(LIGHT_CONTENT_TONE))
    }
}

private const val MID_TONE = 50.0
private const val LIGHT_CONTAINER_TONE = 90.0
private const val LIGHT_CONTENT_TONE = 10.0
private const val DARK_CONTAINER_TONE = 30.0
private const val DARK_CONTENT_TONE = 90.0
