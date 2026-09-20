package io.github.aedev.flow.bilibili

/**
 * Bilibili's service id in Flow's saved data (subscriptions, history, playlists). It was
 * PipePipeExtractor's id for Bilibili, and stays so that data saved earlier keeps its meaning.
 */
const val BILIBILI_SERVICE_ID = 5

/*
 * Which service owns a saved id. The id is the surer test than the stored service id: rows saved
 * before Bilibili had its own service id carry 0, and neither kind of Bilibili id can be a YouTube
 * one. Every place that reads or writes a service id next to a video or channel id goes through here.
 */

/** [saved] unless [videoId] has Bilibili's shape ("BV1xx?p=2"). */
fun serviceIdOfVideo(
    videoId: String,
    saved: Int,
): Int = if (BilibiliVideoId.isBilibili(videoId)) BILIBILI_SERVICE_ID else saved

/** [saved] unless [channelId] is a bare number, an uploader's mid. */
fun serviceIdOfChannel(
    channelId: String,
    saved: Int,
): Int = if (BilibiliChannelId.isMid(channelId.trim())) BILIBILI_SERVICE_ID else saved
