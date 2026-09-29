package io.github.aedev.flow.data.video.downloader.resolve

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.video.DefaultDownloadSelection
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.toDownloadFormats
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The streams one run of a download fetches. */
data class ResolvedStreams(
    val video: Format?,
    val audio: Format,
    val durationMs: Long,
)

sealed interface ResolveOutcome {
    data class Resolved(
        val streams: ResolvedStreams,
    ) : ResolveOutcome

    /** Nothing downloadable: the video is gone, blocked, or offers no stream this request can use. */
    data object Unavailable : ResolveOutcome

    /** Only Opus audio exists, which the MP4 writer does not take. */
    data object NoCompatibleAudio : ResolveOutcome
}

/**
 * Turns a [DownloadRequest] into streams with fresh URLs: Bilibili ids through its own API, everything else
 * through the same InnerTube client ladder playback uses (coalesced with it, so a download of the video on
 * screen costs no second request).
 */
@Singleton
class DownloadStreamResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PlayerPreferences,
    ) {
        suspend fun resolve(
            request: DownloadRequest,
            avoidItags: Set<Int> = emptySet(),
        ): ResolveOutcome {
            if (BilibiliLiveId.isLive(request.videoId)) return ResolveOutcome.Unavailable
            if (BilibiliVideoId.isBilibili(request.videoId)) return resolveBilibili(request, avoidItags)
            val result = InnerTubeVideoStreamExtractor.extract(request.videoId) ?: return ResolveOutcome.Unavailable
            if (result.isLive) return ResolveOutcome.Unavailable
            return select(request, result, avoidItags, defaults())
        }

        /** Bilibili's tracks come as plain MP4 URLs, offered to the same selection as YouTube's. */
        private suspend fun resolveBilibili(
            request: DownloadRequest,
            avoidItags: Set<Int>,
        ): ResolveOutcome {
            val (bvid, page) = BilibiliVideoId.parse(request.videoId)
            val playback =
                try {
                    bilibiliApi(context).playback(bvid, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ResolveOutcome.Unavailable
                }
            val (video, audio) = playback.toDownloadFormats()
            return choose(request, video, audio, playback.info.durationSec * 1_000L, avoidItags, defaults())
        }

        private suspend fun defaults(): SelectionDefaults =
            SelectionDefaults(
                height = preferences.defaultDownloadQuality.first().height,
                codec =
                    preferences.defaultDownloadCodec
                        .first()
                        .takeIf { it != VideoCodec.AUTO }
                        ?.codecKey,
                language = preferences.preferredAudioLanguage.first(),
                musicQuality = preferences.musicDownloadQuality.first(),
            )

        internal data class SelectionDefaults(
            val height: Int,
            val codec: String?,
            val language: String?,
            val musicQuality: MusicAudioQuality = MusicAudioQuality.HIGH,
        )

        internal companion object {
            /** The pure choice, separate from extraction so every rule is unit tested. */
            fun select(
                request: DownloadRequest,
                result: VideoExtractionResult,
                avoidItags: Set<Int>,
                defaults: SelectionDefaults,
            ): ResolveOutcome = choose(request, result.videoFormats, result.audioFormats, result.durationMs(), avoidItags, defaults)

            private fun choose(
                request: DownloadRequest,
                allVideo: List<Format>,
                allAudio: List<Format>,
                durationMs: Long,
                avoidItags: Set<Int>,
                defaults: SelectionDefaults,
            ): ResolveOutcome {
                val audioFormats =
                    DownloadStreamPolicy
                        .buildDownloadAudioFormats(allAudio)
                        .filterNot { it.itag in avoidItags }
                val language = request.audioLanguage ?: defaults.language
                // A song is only its audio, so it is the one download the song quality setting shapes.
                val quality = if (request.wantsAudioOnly) defaults.musicQuality else MusicAudioQuality.HIGH

                val audio =
                    request.audioItag
                        ?.let { itag -> audioFormats.firstOrNull { it.itag == itag } }
                        ?.takeIf { DownloadStreamPolicy.isAacFormat(it) }
                        ?: request.audioTrackId?.let { id ->
                            DownloadStreamPolicy.pickAacAudio(audioFormats.filter { it.audioTrack?.id == id }, language, quality)
                        }
                        ?: DownloadStreamPolicy.pickAacAudio(audioFormats, language, quality)
                        ?: return if (allAudio.any { !it.url.isNullOrBlank() }) {
                            ResolveOutcome.NoCompatibleAudio
                        } else {
                            ResolveOutcome.Unavailable
                        }

                if (request.wantsAudioOnly) {
                    return ResolveOutcome.Resolved(ResolvedStreams(video = null, audio = audio, durationMs = durationMs))
                }

                val videoFormats =
                    DownloadStreamPolicy
                        .buildDownloadVideoFormats(allVideo)
                        .filterNot { it.itag in avoidItags }
                request.videoItag
                    ?.let { itag -> videoFormats.firstOrNull { it.itag == itag } }
                    ?.let { return ResolveOutcome.Resolved(ResolvedStreams(it, audio, durationMs)) }

                val height =
                    DefaultDownloadSelection.pickHeight(
                        videoFormats.map(DownloadStreamPolicy::videoHeight),
                        request.targetHeight ?: defaults.height,
                    ) ?: return ResolveOutcome.Unavailable
                val atHeight = videoFormats.filter { DownloadStreamPolicy.videoHeight(it) == height }
                val codec =
                    DefaultDownloadSelection
                        .rankCodecs(atHeight.map(DownloadStreamPolicy::videoCodecKey), request.videoCodec ?: defaults.codec)
                        .firstOrNull() ?: return ResolveOutcome.Unavailable
                val video = atHeight.first { DownloadStreamPolicy.videoCodecKey(it) == codec }
                return ResolveOutcome.Resolved(ResolvedStreams(video, audio, durationMs))
            }

            private fun VideoExtractionResult.durationMs(): Long =
                (audioFormats + videoFormats).firstNotNullOfOrNull { it.approxDurationMs?.toLongOrNull() }
                    ?: playerResponse.videoDetails
                        ?.lengthSeconds
                        ?.toLongOrNull()
                        ?.times(1000L) ?: 0L
        }
    }
