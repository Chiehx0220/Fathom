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
 * JSON serialization for `/api/v1/...`. Field names mirror
 * `io.github.aedev.flow.data.model.Models.kt` (`Video`/`Channel`/`Playlist`/`Comment`/
 * `SearchResult`) as closely as extractor data allows.
 *
 * Deliberately independent of `HtmlRenderer*.kt` - no shared functions, own extraction calls
 * against the same stable extractor APIs.
 */
object ApiRenderer {

    // channelId is the full channel URL, not a bare id.
    @JvmStatic
    fun videoJson(item: InfoItem, serviceId: Int): JSONObject {
        val json = JSONObject()
        val streamItem = item as? StreamInfoItem
        json.put("id", LocalHttpServer.getVideoId(item.url))
        json.put("url", item.url ?: "")
        json.put("serviceId", item.serviceId)
        json.put("title", item.name ?: "")
        json.put("channelName", streamItem?.uploaderName ?: item.name ?: "")
        json.put("channelId", streamItem?.uploaderUrl ?: "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl))
        // Empty, not the placeholder URL, when there's no real avatar - same as commentJson's
        // authorThumbnail. A missing video thumbnail is fine as a generic placeholder photo; a
        // missing channel avatar should fall back to FT.avatar()'s own initial-letter circle
        // instead of an unrelated stock photo standing in for someone's face.
        json.put("channelThumbnailUrl", if (HtmlRendererCommon.hasThumbnail(streamItem?.uploaderAvatarUrl)) HtmlRendererCommon.getThumbnailUrl(streamItem?.uploaderAvatarUrl) else "")
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

    // Full watch-page detail: videoJson() plus StreamInfo-only fields (description, like count,
    // related videos, playback URLs). Points at the existing /stream, /manifest, /subtitles proxy
    // routes rather than re-resolving URLs itself.
    @JvmStatic
    fun videoDetailJson(info: StreamInfo, serviceId: Int): JSONObject {
        val json = JSONObject()
        val infoUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
        json.put("id", LocalHttpServer.getVideoId(info.url))
        json.put("url", info.url ?: "")
        json.put("serviceId", serviceId)
        json.put("title", info.name ?: "")
        json.put("channelName", info.uploaderName ?: "")
        json.put("channelId", info.uploaderUrl ?: "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(info.thumbnails))
        // See videoJson()'s channelThumbnailUrl: empty, not the placeholder, when there's no real avatar.
        json.put("channelThumbnailUrl", if (HtmlRendererCommon.hasThumbnail(info.uploaderAvatars)) HtmlRendererCommon.getThumbnailUrl(info.uploaderAvatars) else "")
        json.put("duration", info.duration.coerceAtLeast(0).toInt())
        json.put("viewCount", info.viewCount.coerceAtLeast(-1))
        json.put("likeCount", info.likeCount.coerceAtLeast(0))
        json.put("uploadDate", info.textualUploadDate ?: "")
        json.put("description", info.description?.content ?: "")
        // Tells the client whether "description" is markup to render or plain text to escape.
        // YouTube gives HTML (links, line breaks); Bilibili's description is built as PLAIN_TEXT
        // (see LocalServerBilibiliStreams.kt).
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

        // Not part of Flow's Video model - points ExoPlayer at this server's proxy routes. isDash
        // matches HtmlRendererWatch.kt's own progressive-vs-DASH decision for the <video> tag.
        val playback = JSONObject()
        val hasVideo = info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty()
        playback.put("isDash", hasVideo)
        playback.put("manifestUrl", "/manifest?serviceId=$serviceId&id=$infoUrlEncoded")
        playback.put("streamUrl", "/stream?serviceId=$serviceId&id=$infoUrlEncoded")

        // Download formats: progressive (video+audio combined) streams only - each a direct
        // /stream?itag=<itag> URL needing no muxing, via handleStreamProxy's existing itag
        // selection. Plus one highest-bitrate audio-only entry for an audio-only option.
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

        // Caption track list for a client picker menu - points at the existing handleSubtitlesProxy
        // route, no new extraction.
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
            // info.subtitles throws for services/videos with no captions - same best-effort
            // handling as handleSubtitlesProxy.
        }
        json.put("subtitles", subtitles)

        // Chapters with their preview pictures, for a client's chapter strip and seek-bar marks.
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
    fun channelJson(channel: ChannelHeader, isSubscribed: Boolean): JSONObject {
        val json = JSONObject()
        json.put("id", channel.url)
        json.put("name", channel.name)
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(channel.avatarUrl))
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
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl))
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
        // Empty, not the placeholder URL, when there's no real avatar.
        json.put(
            "authorThumbnail",
            if (HtmlRendererCommon.hasThumbnail(item.uploaderAvatarUrl)) HtmlRendererCommon.getThumbnailUrl(item.uploaderAvatarUrl) else "",
        )
        json.put("text", item.commentText.content ?: "")
        json.put("likeCount", item.likeCount.coerceAtLeast(0))
        json.put("publishedTime", item.textualUploadDate ?: "")
        json.put("isPinned", item.isPinned)
        json.put("authorChannelId", item.uploaderUrl ?: "")
        json.put("replyCount", item.replyCount.coerceAtLeast(0))
        json.put("continuationToken", serializePageOrNull(item.replies))
        return json
    }

    // Reuses HtmlRendererCommon's Base64 serializePage()/deserializePage() round-trip, same as
    // every HTML listing page's "Load More" link.
    @JvmStatic
    fun serializePageOrNull(page: Page?): String? = HtmlRendererCommon.serializePage(page)

    @JvmStatic
    fun infoItemsToJson(items: List<InfoItem>, serviceId: Int): JSONArray {
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

    // Mirrors Flow's SearchResult(videos, channels, playlists) shape - splits a mixed InfoItem
    // list into the three buckets by type rather than leaving the caller to do it.
    @JvmStatic
    fun searchResultJson(items: List<InfoItem>, serviceId: Int, nextPage: Page?): JSONObject {
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
