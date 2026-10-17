package io.github.aedev.flow.data.video.downloader.resolve

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.toDownloadFormats
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the downloads of Bilibili ids through Bilibili's own API; [DownloadStreamResolver] hands it every
 * request first and carries on with InnerTube when this answers `null`. The tracks are plain MP4 URLs, offered
 * to the same selection as YouTube's.
 */
@Singleton
class BilibiliDownloadResolver
    @Inject
    constructor(
        private val api: BilibiliApi,
    ) {
        internal suspend fun resolve(
            request: DownloadRequest,
            avoidItags: Set<Int>,
            defaults: suspend () -> DownloadStreamResolver.SelectionDefaults,
        ): ResolveOutcome? {
            if (BilibiliLiveId.isLive(request.videoId)) return ResolveOutcome.Unavailable
            if (!BilibiliVideoId.isBilibili(request.videoId)) return null
            val (bvid, page) = BilibiliVideoId.parse(request.videoId)
            val playback =
                try {
                    api.playback(bvid, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ResolveOutcome.Unavailable
                }
            val (video, audio) = playback.toDownloadFormats()
            return DownloadStreamResolver.choose(request, video, audio, playback.info.durationSec * 1_000L, avoidItags, defaults())
        }
    }
