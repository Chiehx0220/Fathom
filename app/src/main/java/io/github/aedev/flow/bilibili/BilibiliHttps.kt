package io.github.aedev.flow.bilibili

/** http → https (covers, avatars). */
internal fun String.toHttps(): String = replace("http:", "https:")
