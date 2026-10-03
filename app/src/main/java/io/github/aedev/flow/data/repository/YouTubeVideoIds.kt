package io.github.aedev.flow.data.repository

import io.github.aedev.flow.bilibili.BilibiliVideoId

/** False for another service's id: sent to YouTube it only draws a 404. */
internal val String.isYouTubeVideoId: Boolean get() = !BilibiliVideoId.isBilibili(this)
