package io.github.aedev.flow.bilibili

import org.schabi.newpipe.extractor.ServiceList

/** Bilibili service id in saved data (subscriptions, history, playlists). PipePipeExtractor numbering, kept for saved-data compatibility. */
const val BILIBILI_SERVICE_ID = 5

/*
 * Service of a saved id; id shape decides (rows saved before service ids carry 0).
 * All service-id reads/writes beside a video/channel id go through here.
 */

/** Video without saved service id: YouTube unless [videoId] has Bilibili shape. */
fun serviceIdOfVideo(videoId: String): Int = serviceIdOfVideo(videoId, ServiceList.YouTube.serviceId)

/** [saved] unless [videoId] has Bilibili shape ("BV1xx?p=2"). */
fun serviceIdOfVideo(
    videoId: String,
    saved: Int,
): Int = if (BilibiliRef.isVideoId(videoId)) BILIBILI_SERVICE_ID else saved

/** Channel without saved service id: YouTube unless [channelId] is a bare number (uploader mid). */
fun serviceIdOfChannel(channelId: String): Int = serviceIdOfChannel(channelId, ServiceList.YouTube.serviceId)

/** [saved] unless [channelId] is a bare number (uploader mid). */
fun serviceIdOfChannel(
    channelId: String,
    saved: Int,
): Int = if (BilibiliRef.Uploader.ofId(channelId.trim()) != null) BILIBILI_SERVICE_ID else saved
