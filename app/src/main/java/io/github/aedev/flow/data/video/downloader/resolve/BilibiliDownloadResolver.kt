package io.github.aedev.flow.data.video.downloader.resolve

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.toDownloadFormats
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Bilibili download resolution; [DownloadStreamResolver] asks first, InnerTube fallback on `null`. */
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
            if (BilibiliRef.Live.ofId(request.videoId) != null) return ResolveOutcome.Unavailable
            if (!BilibiliRef.isVideoId(request.videoId)) return null
            val ref = BilibiliRef.Video.parse(request.videoId)
            val playback =
                try {
                    api.playback(ref.bvid, ref.part)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ResolveOutcome.Unavailable
                }
            val (video, audio) = playback.toDownloadFormats()
            return DownloadStreamResolver.choose(request, video, audio, playback.info.durationSec * 1_000L, avoidItags, defaults())
        }
    }
