package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelPageKey
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper

/** Newest uploads of one Bilibili uploader; shared by subscription feed and home feed. */
internal suspend fun BilibiliApi.latestVideos(
    mid: Long,
    limit: Int,
    channelName: String,
    channelAvatarUrl: String,
): List<Video> =
    channelVideos(mid, BilibiliChannelPageKey(page = 1, lastAid = 0L))
        .videos
        .take(limit)
        .map { BilibiliVideoMapper.videoFromChannel(it, mid, channelName, channelAvatarUrl) }
