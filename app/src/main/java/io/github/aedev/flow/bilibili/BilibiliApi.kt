/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/extractors/BillibiliStreamExtractor.java
 * (onFetchPage, non-live and non-bangumi branch; buildVideoOnlyStreamsArray / getAudioStreams)
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json

/**
 * Bilibili's web API, in the same role InnerTube plays for YouTube: one method per endpoint plus the
 * orchestration a playback needs. Regular UGC videos only for now; bangumi/premium, live and the
 * app-signed endpoints are not ported yet.
 *
 * @param session owns the cookies, device headers and WBI key, and is shared across calls.
 * @param loggedInCookie the user's own Bilibili cookie string, if they signed in. Only then does
 *   Bilibili hand out 1080p+ and skip the try_look trial mode.
 */
class BilibiliApi(
    private val session: BilibiliSession,
    private val loggedInCookie: () -> String? = { null },
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    /** Metadata for [bvid]; [page] is the 1-based part, as in the "?p=" URL parameter. */
    suspend fun videoInfo(
        bvid: String,
        page: Int = 1,
    ): BilibiliVideoInfo {
        // PipePipe sends this one request with an empty Cookie header and nothing else.
        val body = session.get("$VIEW_URL?bvid=$bvid", mapOf("Cookie" to ""))
        val response = json.decodeFromString<ViewResponse>(body)
        val data = response.data
        if (response.code != 0 || data == null) {
            throw BilibiliContentNotAvailableException(
                response.message.ifBlank { "Could not get Bilibili video metadata" },
            )
        }
        val part =
            data.pages.getOrNull(page - 1)
                ?: throw BilibiliContentNotAvailableException("Part $page does not exist")
        return BilibiliVideoInfo(
            bvid = bvid,
            aid = data.aid,
            cid = part.cid,
            title = data.title,
            description = data.desc,
            thumbnailUrl = data.pic.replace("http:", "https:"),
            durationSec = part.duration,
            uploader = BilibiliUploader(data.owner.mid, data.owner.name, data.owner.face.replace("http:", "https:")),
            viewCount = data.stat.view,
            likeCount = data.stat.like,
            uploadTimeSec = data.pubdate,
            pages = data.pages.map { BilibiliPage(it.cid, it.page, it.part, it.duration) },
            isPaid = data.rights.pay == 1,
        )
    }

    /** Metadata plus the playable streams for one part of [bvid]. */
    suspend fun playback(
        bvid: String,
        page: Int = 1,
    ): BilibiliPlayback {
        val info = videoInfo(bvid, page)
        val originalUrl = "https://www.bilibili.com/video/$bvid"

        val params = LinkedHashMap<String, String>()
        params["avid"] = BilibiliSigning.bv2av(bvid).toString()
        params["bvid"] = bvid
        params["cid"] = info.cid.toString()
        params["qn"] = "120"
        params["fnver"] = "0"
        params["fnval"] = "4048"
        params["fourk"] = "1"

        val headers = session.headers(originalUrl)
        val cookie = loggedInCookie()
        if (cookie != null) {
            headers["Cookie"] = cookie
        } else {
            // https://codeberg.org/NullPointerException/PipePipe/issues/42
            params["try_look"] = "1"
        }
        params["web_location"] = "1315873"
        params.putAll(BilibiliSigning.dmImgParams())

        val body = session.get(session.signedUrl(PLAYURL_URL, params), headers)
        val response = json.decodeFromString<PlayUrlResponse>(body)
        if (response.code != 0) {
            val message = response.message
            if (message.contains("地区")) throw BilibiliGeoRestrictedException(message)
            throw BilibiliContentNotAvailableException(message.ifBlank { "Bilibili code ${response.code}" })
        }
        val dash = response.data?.dash
        if (dash == null || (dash.video.isEmpty() && dash.audio.isEmpty())) {
            throw BilibiliPaidContentException("Paid content")
        }

        // Highest-quality audio first: FLAC, then Dolby, then the regular tracks, as PipePipe orders them.
        val audioItems =
            listOfNotNull(dash.flac?.audio) + (dash.dolby?.audio.orEmpty()) + dash.audio

        val playback =
            BilibiliPlayback(
                info = info,
                videoFormats = dash.video.mapNotNull { it.toFormat() },
                audioFormats = audioItems.mapNotNull { it.toFormat() },
                requestHeaders = session.userAgentHeaders(originalUrl),
            )
        if (info.isPaid && playback.videoFormats.isEmpty() && playback.audioFormats.isEmpty()) {
            throw BilibiliPaidContentException("Paid content")
        }
        return playback
    }

    private fun PlayUrlResponse.DashItem.toFormat(): BilibiliStreamFormat? {
        if (baseUrl.isEmpty()) return null
        return BilibiliStreamFormat(
            id = id,
            url = baseUrl,
            backupUrls = backupUrl.orEmpty(),
            codecs = codecs,
            bandwidth = bandwidth,
            width = width,
            height = height,
            frameRate = frameRate,
            initRange = segmentBase?.initialization?.toRange(),
            indexRange = segmentBase?.indexRange?.toRange(),
        )
    }

    private fun String.toRange(): LongRange? {
        val parts = split("-")
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        return start..end
    }

    companion object {
        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/wbi/view"
        const val PLAYURL_URL = "https://api.bilibili.com/x/player/wbi/playurl"
    }
}
