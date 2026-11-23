package io.github.aedev.flow.localserver

/** MSE `codecs`: AV1 tier letter (`M`/`H`) uppercase; lowercase rejected → video AdaptationSet dropped. */
internal fun String.forMse(): String {
    if (!startsWith("av01.", ignoreCase = true)) return this
    val parts = split('.').toMutableList()
    val levelAndTier = parts.getOrNull(2) ?: return this
    if (levelAndTier.length < 2 || !levelAndTier.last().isLetter()) return this
    parts[2] = levelAndTier.dropLast(1) + levelAndTier.last().uppercaseChar()
    return parts.joinToString(".")
}
