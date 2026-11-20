package io.github.aedev.flow.content

import io.github.aedev.flow.player.stream.model.MediaFormat

/** File extension of the container, as the local server's format lists show it. */
val MediaFormat.suffix: String
    get() =
        when (this) {
            MediaFormat.MPEG_4 -> "mp4"
            MediaFormat.v3GPP -> "3gp"
            MediaFormat.WEBM, MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "webm"
            MediaFormat.M4A -> "m4a"
        }
