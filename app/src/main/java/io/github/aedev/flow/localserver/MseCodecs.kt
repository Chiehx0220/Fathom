package io.github.aedev.flow.localserver

/**
 * The `codecs` string as a browser's MediaSource accepts it. The shared stream model keeps the lower-cased string the player
 * uses, but AV1's tier letter (`M` or `H`, after the level digits) is case-sensitive in MSE: `av01.0.08m.08` is rejected, the
 * video AdaptationSet is dropped and web playback is audio-only.
 */
internal fun String.forMse(): String {
    if (!startsWith("av01.", ignoreCase = true)) return this
    val parts = split('.').toMutableList()
    val levelAndTier = parts.getOrNull(2) ?: return this
    if (levelAndTier.length < 2 || !levelAndTier.last().isLetter()) return this
    parts[2] = levelAndTier.dropLast(1) + levelAndTier.last().uppercaseChar()
    return parts.joinToString(".")
}
