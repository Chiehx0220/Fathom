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
 * Bilibili download resolution via Bilibili API; [DownloadStreamResolver] asks first, falls back to InnerTube on `null`.
 * Plain MP4 URLs, same selection as YouTube.
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
