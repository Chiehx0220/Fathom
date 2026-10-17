package io.github.aedev.flow.localserver

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * `/api/v1/...` JSON. Field names mirror `Models.kt` (`Video`/`Channel`/`Playlist`/`Comment`/`SearchResult`).
 * Independent of `HtmlRenderer*.kt`: no shared functions, own extraction calls.
 */
object ApiRenderer {
    // channelId = full channel URL.
    @JvmStatic
    fun videoJson(
        item: InfoItem,
        serviceId: Int,
    ): JSONObject {
        val json = JSONObject()
        val streamItem = item as? StreamInfoItem
        json.put("id", LocalServerMedia.getVideoId(item.url))
        json.put("url", item.url ?: "")
        json.put("serviceId", item.serviceId)
        json.put("title", item.name ?: "")
        json.put("channelName", streamItem?.uploaderName ?: item.name ?: "")
        json.put("channelId", streamItem?.uploaderUrl ?: "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl))
        // Empty (not placeholder) without a real avatar, as commentJson authorThumbnail; client falls back to FT.avatar() initial circle.
        json.put(
            "channelThumbnailUrl",
            if (HtmlRendererCommon.hasThumbnail(
                    streamItem?.uploaderAvatarUrl,
                )
            ) {
                sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(streamItem?.uploaderAvatarUrl))
            } else {
                ""
            },
        )
        if (streamItem != null) {
            json.put("duration", streamItem.duration.coerceAtLeast(0).toInt())
            json.put("viewCount", streamItem.viewCount.coerceAtLeast(-1))
            json.put("uploadDate", streamItem.textualUploadDate ?: "")
            json.put("isLive", streamItem.streamType == StreamType.LIVE_STREAM || streamItem.streamType == StreamType.AUDIO_LIVE_STREAM)
            json.put("isShort", streamItem.duration in 1..120)
        } else {
            json.put("duration", 0)
            json.put("viewCount", -1)
            json.put("uploadDate", "")
            json.put("isLive", false)
            json.put("isShort", false)
        }
        return json
    }

    // Watch-page detail: videoJson() + StreamInfo fields (description, like count, related, playback URLs); points at /stream, /hlsvod, /subtitles proxies.
    @JvmStatic
    fun videoDetailJson(
        info: StreamInfo,
        serviceId: Int,
    ): JSONObject {
        val json = JSONObject()
        val infoUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
        json.put("id", LocalServerMedia.getVideoId(info.url))
        json.put("url", info.url ?: "")
        json.put("serviceId", serviceId)
        json.put("title", info.name ?: "")
        json.put("channelName", info.uploaderName ?: "")
        json.put("channelId", info.uploaderUrl ?: "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(info.thumbnails))
        // See videoJson() channelThumbnailUrl: empty without a real avatar.
        json.put(
            "channelThumbnailUrl",
            if (HtmlRendererCommon.hasThumbnail(
                    info.uploaderAvatars,
                )
            ) {
                sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(info.uploaderAvatars))
            } else {
                ""
            },
        )
        json.put("duration", info.duration.coerceAtLeast(0).toInt())
        json.put("viewCount", info.viewCount.coerceAtLeast(-1))
        json.put("likeCount", info.likeCount.coerceAtLeast(0))
        json.put("uploadDate", info.textualUploadDate ?: "")
        json.put("description", info.description?.content ?: "")
        // "description" markup flag: YouTube HTML; Bilibili PLAIN_TEXT (LocalServerBilibiliStreams.kt).
        json.put("descriptionType", if (info.description?.type == org.schabi.newpipe.extractor.stream.Description.HTML) "html" else "text")
        json.put("isLive", info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM)
        json.put("isShort", info.duration in 1..120)

        val related = JSONArray()
        if (!info.relatedItems.isNullOrEmpty()) {
            for (relatedItem in info.relatedItems) {
                if (relatedItem is StreamInfoItem) {
                    related.put(videoJson(relatedItem, serviceId))
                }
            }
        }
        json.put("relatedVideos", related)

        // Not in Video model: ExoPlayer → this server's proxy routes.
        val playback = JSONObject()
        // ladder = video-only streams; combined-only video: no HLS rungs
        val hasLadder = StreamCatalog.of(InfoStreams(info), highest = true).videos.isNotEmpty()
        val hasVideo = hasLadder || !info.hlsUrl.isNullOrEmpty()
        val isBilibiliLive =
            LocalServerBilibili.isBilibili(serviceId) && info.streamType == StreamType.LIVE_STREAM && !info.hlsUrl.isNullOrEmpty()
        playback.put("isAdaptive", hasVideo && !isBilibiliLive)
        if (isBilibiliLive) {
            // Room playlist via relay: CDN needs a Referer browsers cannot send.
            playback.put("hlsUrl", hlsRelayPath(info.hlsUrl.orEmpty()))
            playback.put("liveRoom", info.id)
        }
        val qualities = JSONArray()
        for (q in downloadQualities(info.videoStreams.orEmpty() + info.videoOnlyStreams.orEmpty())) {
            qualities.put(JSONObject().put("height", q.height).put("codecs", JSONArray(q.codecs)))
        }
        playback.put("qualities", qualities)
        playback.put("playlistUrl", "/hlsvod?serviceId=$serviceId&id=$infoUrlEncoded")
        playback.put("streamUrl", "/stream?serviceId=$serviceId&id=$infoUrlEncoded")

        // Download formats: progressive (muxed) streams only, direct `/stream?itag=<itag>` (handleStreamProxy itag selection),
        // plus highest-bitrate audio-only entry.
        val formats = JSONArray()
        for (stream in info.videoStreams) {
            if (stream.isVideoOnly) continue
            val format = JSONObject()
            format.put("itag", stream.itag)
            format.put("kind", "video")
            format.put("resolution", stream.resolution ?: "")
            format.put("format", stream.format?.suffix ?: "")
            format.put("bitrate", stream.bitrate)
            formats.put(format)
        }
        info.audioStreams?.maxByOrNull { it.averageBitrate }?.let { bestAudio ->
            val format = JSONObject()
            format.put("itag", bestAudio.itag)
            format.put("kind", "audio")
            format.put("resolution", "")
            format.put("format", bestAudio.format?.suffix ?: "")
            format.put("bitrate", bestAudio.averageBitrate)
            formats.put(format)
        }
        playback.put("formats", formats)

        json.put("playback", playback)

        // Caption track list for client picker; handleSubtitlesProxy route.
        val subtitles = JSONArray()
        try {
            for (sub in info.subtitles.orEmpty()) {
                val track = JSONObject()
                track.put("languageTag", sub.languageTag ?: "")
                track.put("displayName", sub.displayLanguageName ?: sub.languageTag ?: "")
                track.put("isAutoGenerated", sub.isAutoGenerated)
                track.put(
                    "url",
                    "/subtitles?serviceId=$serviceId&id=$infoUrlEncoded" +
                        "&lang=${HtmlRendererCommon.encodeUrl(sub.languageTag ?: "")}" +
                        "&auto=${sub.isAutoGenerated}",
                )
                subtitles.put(track)
            }
        } catch (e: Exception) {
            // info.subtitles throws without captions; best-effort, as handleSubtitlesProxy.
        }
        json.put("subtitles", subtitles)

        // Chapters + preview pictures (chapter strip, seek-bar marks).
        val chapters = JSONArray()
        for (segment in info.streamSegments.orEmpty()) {
            val chapter = JSONObject()
            chapter.put("s", segment.startTimeSeconds)
            chapter.put("t", segment.title ?: "")
            val preview = segment.previewUrl
            chapter.put("thumb", if (preview.isNullOrBlank()) "" else HtmlRendererCommon.getThumbnailUrl(preview))
            chapters.put(chapter)
        }
        json.put("chapters", chapters)

        return json
    }

    @JvmStatic
    fun channelJson(
        channel: ChannelHeader,
        isSubscribed: Boolean,
    ): JSONObject {
        val json = JSONObject()
        json.put("id", channel.url)
        json.put("name", channel.name)
        json.put("thumbnailUrl", sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(channel.avatarUrl)))
        json.put("subscriberCount", channel.subscriberCount.coerceAtLeast(-1))
        json.put("description", channel.description)
        json.put("isSubscribed", isSubscribed)
        json.put("url", channel.url)
        return json
    }

    @JvmStatic
    fun channelInfoItemJson(item: ChannelInfoItem): JSONObject {
        val json = JSONObject()
        json.put("id", item.url)
        json.put("serviceId", item.serviceId)
        json.put("name", item.name ?: "")
        json.put("thumbnailUrl", sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl)))
        json.put("subscriberCount", item.subscriberCount.coerceAtLeast(-1))
        json.put("url", item.url)
        return json
    }

    @JvmStatic
    fun playlistInfoItemJson(item: PlaylistInfoItem): JSONObject {
        val json = JSONObject()
        json.put("id", item.url)
        json.put("serviceId", item.serviceId)
        json.put("name", item.name ?: "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl))
        json.put("videoCount", item.streamCount.coerceAtLeast(0).toInt())
        json.put("isLocal", false)
        return json
    }

    @JvmStatic
    fun commentJson(item: CommentsInfoItem): JSONObject {
        val json = JSONObject()
        json.put("id", item.commentId ?: "")
        json.put("author", item.uploaderName ?: "")
        // Empty without a real avatar.
        json.put(
            "authorThumbnail",
            if (HtmlRendererCommon.hasThumbnail(
                    item.uploaderAvatarUrl,
                )
            ) {
                sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(item.uploaderAvatarUrl))
            } else {
                ""
            },
        )
        json.put("text", item.commentText.content ?: "")
        // As videoDetailJson descriptionType: comment text may be HTML.
        json.put("textType", if (item.commentText.type == org.schabi.newpipe.extractor.stream.Description.HTML) "html" else "text")
        json.put("likeCount", item.likeCount.coerceAtLeast(0))
        json.put("publishedTime", item.textualUploadDate ?: "")
        json.put("isPinned", item.isPinned)
        json.put("authorChannelId", item.uploaderUrl ?: "")
        json.put("replyCount", item.replyCount.coerceAtLeast(0))
        json.put("continuationToken", serializePageOrNull(item.replies))
        return json
    }

    // HtmlRendererCommon Base64 serializePage()/deserializePage() round-trip, as HTML "Load More".
    @JvmStatic
    fun serializePageOrNull(page: Page?): String? = HtmlRendererCommon.serializePage(page)

    @JvmStatic
    fun infoItemsToJson(
        items: List<InfoItem>,
        serviceId: Int,
    ): JSONArray {
        val array = JSONArray()
        for (item in items) {
            when (item) {
                is StreamInfoItem -> array.put(videoJson(item, serviceId))
                is ChannelInfoItem -> array.put(channelInfoItemJson(item))
                is PlaylistInfoItem -> array.put(playlistInfoItemJson(item))
                else -> array.put(videoJson(item, serviceId))
            }
        }
        return array
    }

    // Flow SearchResult(videos, channels, playlists): mixed InfoItem list split by type.
    @JvmStatic
    fun searchResultJson(
        items: List<InfoItem>,
        serviceId: Int,
        nextPage: Page?,
    ): JSONObject {
        val videos = JSONArray()
        val channels = JSONArray()
        val playlists = JSONArray()
        for (item in items) {
            when (item) {
                is StreamInfoItem -> videos.put(videoJson(item, serviceId))
                is ChannelInfoItem -> channels.put(channelInfoItemJson(item))
                is PlaylistInfoItem -> playlists.put(playlistInfoItemJson(item))
                else -> videos.put(videoJson(item, serviceId))
            }
        }
        val json = JSONObject()
        json.put("videos", videos)
        json.put("channels", channels)
        json.put("playlists", playlists)
        json.put("nextPage", serializePageOrNull(nextPage))
        return json
    }

    @JvmStatic
    fun errorJson(message: String?): String {
        val json = JSONObject()
        json.put("error", message ?: "Unknown error")
        return json.toString()
    }
}
