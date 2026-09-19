package io.github.aedev.flow.data.repository

import android.util.Log
import android.util.LruCache
import io.github.aedev.flow.data.comments.CommentsPageResult
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.data.model.isYouTube
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.model.needsCollaboratorResolution
import io.github.aedev.flow.data.shorts.ChannelReelIndex
import io.github.aedev.flow.data.shorts.ShortsClassifier
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.response.VideoChapter
import io.github.aedev.flow.innertube.models.response.VideoChaptersParser
import io.github.aedev.flow.innertube.models.response.VideoHeatmap
import io.github.aedev.flow.innertube.models.response.VideoHeatmapParser
import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import io.github.aedev.flow.innertube.pages.VideoDescriptionPage
import io.github.aedev.flow.player.stream.InFlightRequestCoalescer
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.RelativeUploadDateParser
import io.github.aedev.flow.utils.SearchFilterResolver
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.github.aedev.flow.utils.bestImageUrl
import io.github.aedev.flow.utils.distinctBestImageUrls
import io.github.aedev.flow.utils.newPipeLocalization
import io.github.aedev.flow.utils.parseRelativeToTimestamp
import io.github.aedev.flow.utils.parseToTimestamp
import io.github.aedev.flow.utils.resolveNonYouTubeChannelId
import io.github.aedev.flow.utils.resolveNonYouTubeChannelUrl
import io.github.aedev.flow.utils.resolveNonYouTubePlaylistId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.compat.ListExtractorCompat
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository
    @Inject
    constructor(
        private val playerPreferences: PlayerPreferences,
        private val channelReelIndex: ChannelReelIndex,
    ) {
        private val service = ServiceList.YouTube

        private val returnYouTubeDislikeCoalescer =
            InFlightRequestCoalescer<String, ReturnYouTubeDislikeCounts?>(
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )

        private val watchNextCoalescer =
            InFlightRequestCoalescer<String, JsonElement?>(
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )

        private val videoCategoryCoalescer =
            InFlightRequestCoalescer<String, String?>(
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )

        // A category never changes, so this is a plain memo rather than a short player-side cache:
        // the value is worth keeping for every video the engine has already learned from.
        private val videoCategoryCache = VideoCategoryMemo(VIDEO_CATEGORY_CACHE_SIZE)

        // The comment section, the attributed description and the related lane all read one watch
        // response, so it is fetched once per video and held for the few videos in play.
        private val watchNextCache = LruCache<String, JsonElement>(WATCH_NEXT_CACHE_SIZE)

        // Cache for channel avatar URLs to avoid redundant network calls
        private val channelAvatarCache = LruCache<String, String>(300)
        private val videoAvatarStackCache = LruCache<String, List<String>>(300)
        private val videoCollaboratorCache = LruCache<String, List<VideoCollaborator>>(300)
        private val videoChannelMetadataCache = LruCache<String, VideoChannelMetadata>(300)

        private data class VideoChannelMetadata(
            val channelId: String,
            val channelName: String,
            val avatarUrl: String,
        )

        /**
         * Fetch channel avatar by channelId, with in-memory caching.
         * Returns empty string on failure.
         */
        suspend fun fetchChannelAvatarById(channelId: String): String =
            withContext(Dispatchers.IO) {
                if (channelId.isBlank()) return@withContext ""
                channelAvatarCache[channelId]?.let { return@withContext it }
                val info = getChannelInfo(channelId) ?: return@withContext ""
                val url = info.avatars.maxByOrNull { it.height }?.url ?: ""
                if (url.isNotEmpty()) channelAvatarCache.put(channelId, url)
                url
            }

        /**
         * Enrich a list of [Video] objects that are missing [Video.channelThumbnailUrl]
         * by fetching avatar URLs in parallel (max 5 concurrent channel fetches).
         */
        suspend fun enrichVideosWithAvatars(videos: List<Video>): List<Video> =
            supervisorScope {
                val channelIds =
                    videos
                        .filter { it.channelThumbnailUrl.isEmpty() && it.channelId.isNotEmpty() }
                        .map { it.channelId }
                        .distinct()

                if (channelIds.isEmpty()) return@supervisorScope videos

                Log.d(TAG, "enrichVideosWithAvatars: fetching avatars for ${channelIds.size} channels")
                val avatarMap = mutableMapOf<String, String>()
                channelIds.chunked(5).forEach { batch ->
                    batch
                        .map { id ->
                            async(Dispatchers.IO) { withTimeoutOrNull(6_000L) { id to fetchChannelAvatarById(id) } }
                        }.awaitAll()
                        .forEach { pair ->
                            pair?.let { (id, url) -> if (url.isNotEmpty()) avatarMap[id] = url }
                        }
                }
                Log.d(TAG, "enrichVideosWithAvatars: resolved ${avatarMap.size}/${channelIds.size} avatars")
                if (avatarMap.isEmpty()) return@supervisorScope videos
                videos.map { video ->
                    if (video.channelThumbnailUrl.isEmpty()) {
                        avatarMap[video.channelId]?.let { avatar ->
                            video.copy(
                                channelThumbnailUrl = avatar,
                                channelThumbnailUrls = video.channelThumbnailUrls.ifEmpty { listOf(avatar) },
                            )
                        } ?: video
                    } else {
                        video
                    }
                }
            }

        suspend fun enrichMissingChannelMetadata(
            videos: List<Video>,
            limit: Int = 10,
        ): List<Video> =
            supervisorScope {
                val candidates =
                    videos
                        .filter { video ->
                            video.id.isNotBlank() &&
                                (
                                    video.channelId.isBlank() ||
                                        !video.channelId.startsWith("UC") ||
                                        video.channelThumbnailUrl.isBlank()
                                )
                        }.take(limit)
                if (candidates.isEmpty()) return@supervisorScope videos

                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                val metadataByVideoId =
                    candidates
                        .map { video ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    val metadata = resolveVideoChannelMetadata(video)
                                    if (metadata != null &&
                                        metadata.channelId.isNotBlank() &&
                                        metadata.avatarUrl.isNotBlank()
                                    ) {
                                        videoChannelMetadataCache.put(video.id, metadata)
                                    }
                                    video.id to metadata
                                }
                            }
                        }.awaitAll()
                        .mapNotNull { (videoId, metadata) ->
                            metadata?.let { videoId to it }
                        }.toMap()

                if (metadataByVideoId.isEmpty()) return@supervisorScope videos
                videos.map { video ->
                    val metadata = metadataByVideoId[video.id] ?: return@map video
                    val avatarUrl = metadata.avatarUrl.ifBlank { video.channelThumbnailUrl }
                    video.copy(
                        channelId = metadata.channelId.ifBlank { video.channelId },
                        channelName = metadata.channelName.ifBlank { video.channelName },
                        channelThumbnailUrl = avatarUrl,
                        channelThumbnailUrls =
                            if (avatarUrl.isNotBlank()) {
                                (listOf(avatarUrl) + video.channelThumbnailUrls).distinct()
                            } else {
                                video.channelThumbnailUrls
                            },
                    )
                }
            }

        private suspend fun resolveVideoChannelMetadata(video: Video): VideoChannelMetadata? {
            videoChannelMetadataCache[video.id]?.let { return it }

            val channelMetadata =
                video.channelId.takeIf { it.isNotBlank() }?.let { channelId ->
                    withTimeoutOrNull(6_000L) {
                        getChannelInfo(channelId)?.let { info ->
                            VideoChannelMetadata(
                                channelId = info.id.orEmpty(),
                                channelName = info.name.orEmpty(),
                                avatarUrl =
                                    info.avatars
                                        .maxByOrNull { it.height }
                                        ?.url
                                        .orEmpty(),
                            )
                        }
                    }
                }

            if (channelMetadata?.avatarUrl?.isNotBlank() == true) return channelMetadata

            val watchMetadata =
                withTimeoutOrNull(5_000L) {
                    getLiveWatchMetadata(video.id)?.let { result ->
                        VideoChannelMetadata(
                            channelId = result.channelId.orEmpty(),
                            channelName = result.channelName.orEmpty(),
                            avatarUrl = result.channelAvatarUrl.orEmpty(),
                        )
                    }
                }

            val merged =
                VideoChannelMetadata(
                    channelId =
                        watchMetadata?.channelId.orEmpty().ifBlank {
                            channelMetadata?.channelId.orEmpty().ifBlank { video.channelId }
                        },
                    channelName =
                        watchMetadata?.channelName.orEmpty().ifBlank {
                            channelMetadata?.channelName.orEmpty().ifBlank { video.channelName }
                        },
                    avatarUrl =
                        watchMetadata?.avatarUrl.orEmpty().ifBlank {
                            channelMetadata?.avatarUrl.orEmpty()
                        },
                )

            if (merged.avatarUrl.isNotBlank()) return merged

            val fallbackAvatar =
                merged.channelId
                    .takeIf { it.isNotBlank() }
                    ?.let { channelId ->
                        withTimeoutOrNull(6_000L) { fetchChannelAvatarById(channelId) }
                    }.orEmpty()
            return merged.copy(avatarUrl = fallbackAvatar)
        }

        /**
         * Fetch trending videos
         */
        suspend fun getTrendingVideos(
            region: String = "",
            nextPage: Page? = null,
            service: StreamingService = this.service,
        ): Pair<List<Video>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    val effectiveRegion = region.ifBlank { playerPreferences.trendingRegion.first() }
                    NewPipe.setupLocalization(
                        newPipeLocalization(playerPreferences.appLanguage.first()),
                        newPipeContentCountry(effectiveRegion),
                    )

                    val kioskList = service.kioskList
                    // Each service names its "trending" kiosk id differently (this fork's YouTube
                    // entry is literally keyed "Recommended Lives", Bilibili's is "Recommended
                    // Videos") - the default kiosk is the one thing every service declares
                    // consistently, so ask for that instead of a hardcoded "Trending" id that no
                    // service here actually uses.
                    val trendingExtractor = kioskList.getDefaultKioskExtractor(null) as KioskExtractor<*>

                    // FIX: ALWAYS call fetchPage to initialize the extractor state
                    trendingExtractor.fetchPage()

                    val infoItems =
                        if (nextPage != null) {
                            trendingExtractor.getPage(nextPage)
                        } else {
                            trendingExtractor.initialPage
                        }

                    val videos =
                        infoItems.items
                            .filterIsInstance<StreamInfoItem>()
                            .map { item -> item.toVideo(service) }

                    val enriched =
                        if (service.isYouTube) {
                            enrichLikelyCollabAvatarStacks(videos)
                        } else {
                            videos
                        }
                    Pair(enriched, infoItems.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "Trending unavailable: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        /**
         * Fetch YouTube Shorts specifically
         * Uses search with #shorts and duration filtering
         */
        suspend fun getShorts(nextPage: Page? = null): Pair<List<Video>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    // Search for #shorts which often returns actual shorts
                    val searchExtractor =
                        service.getSearchExtractor(
                            "#shorts",
                            SearchFilterResolver.resolveSearchContentFilters(service, emptyList()),
                            emptyList(),
                        )
                    val infoItems = ListExtractorCompat.fetchInitialOrPage(searchExtractor, nextPage)

                    val shorts =
                        infoItems.items
                            .filterIsInstance<StreamInfoItem>()
                            .map { it.toVideo() }
                            .filter { it.duration in 1..60 } // Actual shorts are <= 60s
                            .sortedByDescending { it.timestamp }

                    Pair(shorts, infoItems.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        /**
         * Search for videos
         */
        suspend fun searchVideos(
            query: String,
            nextPage: Page? = null,
            service: StreamingService = this.service,
        ): Pair<List<Video>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    val searchExtractor =
                        service.getSearchExtractor(
                            query,
                            SearchFilterResolver.resolveSearchContentFilters(service, emptyList()),
                            emptyList(),
                        )
                    val infoItems = ListExtractorCompat.fetchInitialOrPage(searchExtractor, nextPage)

                    val videos =
                        infoItems.items
                            .filterIsInstance<StreamInfoItem>()
                            .map { item -> item.toVideo(service) }

                    val enriched =
                        if (service.isYouTube) {
                            enrichLikelyCollabAvatarStacks(
                                enrichVideosWithSearchAvatarStacks(query, videos),
                            )
                        } else {
                            videos
                        }
                    Pair(enriched, infoItems.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        /**
         * Search with support for different content types (videos, channels, playlists)
         */
        suspend fun search(
            query: String,
            contentFilters: List<String> = emptyList(),
            nextPage: Page? = null,
            service: StreamingService = this.service,
        ): io.github.aedev.flow.data.model.SearchResult =
            withContext(Dispatchers.IO) {
                try {
                    val searchExtractor =
                        service.getSearchExtractor(
                            query,
                            SearchFilterResolver.resolveSearchContentFilters(service, contentFilters),
                            emptyList(),
                        )
                    val infoItems = ListExtractorCompat.fetchInitialOrPage(searchExtractor, nextPage)

                    val videos = mutableListOf<Video>()
                    val channels = mutableListOf<io.github.aedev.flow.data.model.Channel>()
                    val playlists = mutableListOf<io.github.aedev.flow.data.model.Playlist>()

                    infoItems.items.forEach { item ->
                        when (item) {
                            is StreamInfoItem -> {
                                videos.add(item.toVideo(service))
                            }

                            is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                                channels.add(item.toChannel(service))
                            }

                            is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                                playlists.add(item.toPlaylist(service))
                            }
                        }
                    }

                    io.github.aedev.flow.data.model.SearchResult(
                        videos =
                            if (service.isYouTube) {
                                enrichLikelyCollabAvatarStacks(
                                    enrichVideosWithSearchAvatarStacks(query, videos),
                                )
                            } else {
                                videos
                            },
                        channels = channels,
                        playlists = playlists,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    io.github.aedev.flow.data.model
                        .SearchResult()
                }
            }

        private suspend fun enrichVideosWithSearchAvatarStacks(
            query: String,
            videos: List<Video>,
        ): List<Video> {
            if (videos.isEmpty() || videos.all { it.channelThumbnailUrls.size > 1 }) return videos

            val avatarStacks =
                withTimeoutOrNull(4_000L) {
                    YouTube.searchVideoAvatarStacks(query).getOrNull()
                }.orEmpty()
            if (avatarStacks.isEmpty()) return videos

            return videos.map { video ->
                val stack = avatarStacks[video.id].orEmpty()
                if (stack.size <= 1) return@map video

                val merged =
                    (stack + video.channelThumbnailUrls + video.channelThumbnailUrl)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.avatarImageIdentityKey() }
                        .take(3)

                if (merged.size > 1) {
                    video.copy(
                        channelThumbnailUrl = merged.first(),
                        channelThumbnailUrls = merged,
                    )
                } else {
                    video
                }
            }
        }

        suspend fun enrichLikelyCollabAvatarStacks(
            videos: List<Video>,
            limit: Int = 10,
        ): List<Video> =
            supervisorScope {
                val candidates =
                    videos
                        .filter { it.needsCollaboratorResolution() }
                        .take(limit)

                if (candidates.isEmpty()) return@supervisorScope videos

                val fetched =
                    candidates
                        .chunked(3)
                        .flatMap { batch ->
                            batch
                                .map { video ->
                                    async(Dispatchers.IO) {
                                        val collaborators =
                                            videoCollaboratorCache[video.id]
                                                ?: withTimeoutOrNull(4_000L) {
                                                    YouTube.videoCollaborators(video.id).getOrNull()
                                                }.orEmpty().also { items ->
                                                    if (items.isNotEmpty()) {
                                                        videoCollaboratorCache.put(video.id, items)
                                                    }
                                                }
                                        val stack =
                                            collaborators
                                                .map { it.thumbnailUrl }
                                                .filter { it.isNotBlank() }
                                                .ifEmpty {
                                                    videoAvatarStackCache[video.id]
                                                        ?: withTimeoutOrNull(4_000L) {
                                                            YouTube.videoAvatarStack(video.id).getOrNull()
                                                        }.orEmpty().also { urls ->
                                                            videoAvatarStackCache.put(video.id, urls)
                                                        }
                                                }
                                        video.id to (collaborators to stack)
                                    }
                                }.awaitAll()
                        }.filter { (_, result) -> result.first.size > 1 || result.second.size > 1 }
                        .toMap()

                if (fetched.isEmpty()) return@supervisorScope videos

                videos.map { video ->
                    val (collaborators, stack) =
                        fetched[video.id]
                            ?: (emptyList<VideoCollaborator>() to emptyList())
                    if (stack.size <= 1 && collaborators.size <= 1) return@map video

                    val merged =
                        (stack + video.channelThumbnailUrls + video.channelThumbnailUrl)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.avatarImageIdentityKey() }
                            .take(3)

                    if (merged.size > 1 || collaborators.size > 1) {
                        video.copy(
                            channelName =
                                collaborators
                                    .map { it.name }
                                    .filter { it.isNotBlank() }
                                    .takeIf { it.size > 1 }
                                    ?.joinToString(" and ")
                                    ?: video.channelName,
                            channelThumbnailUrl = merged.firstOrNull() ?: video.channelThumbnailUrl,
                            channelThumbnailUrls = merged.ifEmpty { video.channelThumbnailUrls },
                            collaborators = collaborators.ifEmpty { video.collaborators },
                        )
                    } else {
                        video
                    }
                }
            }

        /**
         * Get video stream info for playback.
         *
         * Throws the original exception on failure so callers can display specific, accurate
         * error messages (age restriction, geo-block, private video, etc.) instead of a
         * generic "unknown error".  Callers that want null-on-failure should wrap in
         * try/catch themselves.
         */
        suspend fun getVideoStreamInfo(
            videoId: String,
            service: StreamingService = this.service,
        ): StreamInfo? =
            withContext(Dispatchers.IO) {
                try {
                    val url =
                        if (service.isYouTube) {
                            "https://www.youtube.com/watch?v=$videoId"
                        } else {
                            service.streamLHFactory.getUrl(videoId)
                        }
                    StreamInfo.getInfo(service, url)
                } catch (e: Exception) {
                    // NewPipe "The page needs to be reloaded" error handling
                    // This often happens due to stale internal state or specific YouTube bot identifiers
                    val isReloadError =
                        service.isYouTube &&
                            (
                                e.message?.contains("page needs to be reloaded", ignoreCase = true) == true ||
                                    (
                                        e is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException &&
                                            e.message?.contains("reloaded") == true
                                    )
                            )

                    if (isReloadError) {
                        Log.w(
                            "YouTubeRepository",
                            "Hit 'page needs to be reloaded' error for $videoId. Retrying with fresh state...",
                        )

                        // Re-init NewPipe to potentially clear internal state
                        try {
                            NewPipe.init(
                                NewPipe.getDownloader(),
                                NewPipe.getPreferredLocalization(),
                                NewPipe.getPreferredContentCountry(),
                            )
                        } catch (initEx: Exception) {
                            Log.e("YouTubeRepository", "Failed to re-init NewPipe", initEx)
                        }

                        // Retry with alternate URL format which works as a cache buster sometimes
                        try {
                            val altUrl = "https://youtu.be/$videoId"
                            Log.d("YouTubeRepository", "Retrying with alternate URL: $altUrl")
                            return@withContext StreamInfo.getInfo(service, altUrl)
                        } catch (retryEx: Exception) {
                            Log.e("YouTubeRepository", "Retry failed for $videoId: ${retryEx.message}", retryEx)
                            throw retryEx
                        }
                    } else {
                        Log.e("YouTubeRepository", "Error getting stream info for $videoId: ${e.message}", e)
                        throw e
                    }
                }
            }

        /**
         * Get a single video object by ID
         */
        suspend fun getVideo(
            videoId: String,
            service: StreamingService = this.service,
        ): Video? =
            withContext(Dispatchers.IO) {
                try {
                    val info = getVideoStreamInfo(videoId, service) ?: return@withContext null

                    val bestThumbnail =
                        info.thumbnails
                            .sortedByDescending { it.height }
                            .map { it.url }
                            .firstOrNull()
                            .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }

                    val avatarUrls = info.uploaderAvatars.distinctBestImageUrls()
                    val bestAvatar = avatarUrls.firstOrNull().orEmpty()

                    Video(
                        id = videoId,
                        title = info.name ?: "Unknown Title",
                        channelName = info.uploaderName ?: "Unknown Channel",
                        channelId = extractChannelId(info.uploaderUrl, service),
                        thumbnailUrl = bestThumbnail,
                        duration = info.duration.toInt(),
                        viewCount = info.viewCount,
                        uploadDate = info.textualUploadDate ?: "Unknown",
                        timestamp =
                            resolveUploadTimestamp(
                                info.uploadDate
                                    ?.offsetDateTime()
                                    ?.toInstant()
                                    ?.toEpochMilli(),
                                info.textualUploadDate,
                            ),
                        channelThumbnailUrl = bestAvatar,
                        channelThumbnailUrls = avatarUrls,
                        serviceId = service.serviceId,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    null
                }
            }

        /**
         * Get related videos
         */
        suspend fun getRelatedVideos(videoId: String): List<Video> =
            withContext(Dispatchers.IO) {
                val streamInfo = fetchWatchStreamInfoWithAlternates(videoId) ?: return@withContext emptyList()
                getRelatedVideosFromStreamInfo(streamInfo)
                    .filter { it.id.isNotBlank() && it.id != videoId }
                    .distinctBy { it.id }
            }

        /**
         * Fetch recent uploads for a single channel (by channelId or channel URL).
         * Limits to `limitPerChannel` videos per channel to avoid OOM and long runs.
         */
        suspend fun getChannelUploads(
            channelIdOrUrl: String,
            limitPerChannel: Int = 6,
            service: StreamingService = this.service,
        ): List<Video> =
            withContext(Dispatchers.IO) {
                try {
                    val isYouTube = service.isYouTube

                    // Try to extract a channelId (UC...) from the input
                    val channelId =
                        when {
                            !isYouTube -> null
                            channelIdOrUrl.startsWith("UC") -> {
                                channelIdOrUrl
                            }

                            channelIdOrUrl.contains("/channel/") -> {
                                channelIdOrUrl.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                            }

                            else -> {
                                null
                            }
                        }

                    if (channelId != null && channelId.startsWith("UC")) {
                        val uploadsId = "UU" + channelId.removePrefix("UC")
                        val playlistUrl = "https://www.youtube.com/playlist?list=$uploadsId"
                        val playlistExtractor = service.getPlaylistExtractor(playlistUrl)
                        val page = ListExtractorCompat.fetchInitialOrPage(playlistExtractor, null)
                        val items =
                            page.items
                                .filterIsInstance<StreamInfoItem>()
                                .filterNot { it.isPaidOrMembersOnly() }
                                .take(limitPerChannel)
                                .map { it.toVideo(service) }
                        return@withContext markUploadsPlaylistReels(channelId, items)
                    }

                    // Fallback: attempt to use channel extractor directly (best-effort)
                    val channelUrl =
                        when {
                            channelIdOrUrl.startsWith("http") -> channelIdOrUrl
                            isYouTube -> "https://www.youtube.com/channel/$channelIdOrUrl"
                            else -> resolveNonYouTubeChannelUrl(channelIdOrUrl, service) { channelIdOrUrl }
                        }
                    val extractor = service.getChannelExtractor(channelUrl)
                    val pageItems = ListExtractorCompat.fetchInitialOrPage(extractor, null).items

                    val fallbackItems =
                        pageItems
                            .filterNot { it.isPaidOrMembersOnly() }
                            .take(limitPerChannel)
                            .map { it.toVideo(service) }
                    if (channelId != null) {
                        markUploadsPlaylistReels(channelId, fallbackItems)
                    } else {
                        fallbackItems
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    emptyList()
                }
            }

        private suspend fun markUploadsPlaylistReels(
            channelId: String,
            videos: List<Video>,
        ): List<Video> =
            withTimeoutOrNull(REEL_INDEX_TIMEOUT_MS) {
                channelReelIndex.markReels(channelId, videos)
            } ?: videos

        /**
         * Fetch channel info (best-effort) using NewPipe's channel extractor.
         */
        suspend fun getChannelInfo(
            channelIdOrUrl: String,
            service: StreamingService = this.service,
        ): org.schabi.newpipe.extractor.channel.ChannelInfo? =
            withContext(Dispatchers.IO) {
                try {
                    val value = channelIdOrUrl.trim()
                    val isYouTube = service.isYouTube
                    val channelUrl =
                        when {
                            value.startsWith("http") -> value
                            !isYouTube -> resolveNonYouTubeChannelUrl(value, service) { value }
                            value.startsWith("UC") -> "https://www.youtube.com/channel/$value"
                            value.startsWith("@") -> "https://www.youtube.com/$value"
                            else -> "https://www.youtube.com/@$value"
                        }
                    org.schabi.newpipe.extractor.channel.ChannelInfo
                        .getInfo(service, channelUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    null
                }
            }

        /**
         * Fetches a channel's creator-declared keyword tags (+ description) and
         * feeds them to the recommendation engine — authored channel identity,
         * used for topic profiles and interest clustering. Best-effort.
         */
        suspend fun learnChannelTags(
            context: android.content.Context,
            channelId: String,
        ) {
            val info = getChannelInfo(channelId) ?: return
            val tags = info.tags.orEmpty()
            if (tags.isEmpty() && info.description.isNullOrBlank()) return
            io.github.aedev.flow.data.recommendation.FlowNeuroEngine
                .onChannelTagsLearned(context, channelId, tags, info.description)
        }

        /**
         * PERFORMANCE OPTIMIZED: Aggregate uploads from multiple channels
         * Uses SupervisorScope for error isolation - one failed channel doesn't break others
         * Implements chunked parallel fetching to prevent overwhelming the network
         */
        suspend fun getVideosForChannels(
            channelIdsOrUrls: List<String>,
            perChannelLimit: Int = 5,
            totalLimit: Int = 50,
            service: StreamingService = this.service,
        ): List<Video> =
            withContext(PerformanceDispatcher.networkIO) {
                try {
                    // Use supervisorScope for error isolation
                    // If one channel fails, others continue fetching
                    supervisorScope {
                        // Process in chunks of 5 for optimal parallelism
                        // This prevents overwhelming the network while maintaining speed
                        val chunkSize = 5
                        val combined = mutableListOf<Video>()

                        channelIdsOrUrls.chunked(chunkSize).forEach { chunk ->
                            val chunkResults =
                                chunk
                                    .map { id ->
                                        async(PerformanceDispatcher.networkIO) {
                                            withTimeoutOrNull(8_000L) {
                                                // 8 second timeout per channel
                                                try {
                                                    getChannelUploads(id, perChannelLimit, service)
                                                } catch (e: Exception) {
                                                    Log.w("YouTubeRepository", "Channel fetch failed: ${e.message}")
                                                    emptyList()
                                                }
                                            } ?: emptyList()
                                        }
                                    }.awaitAll()

                            chunkResults.forEach { combined.addAll(it) }
                        }

                        combined
                            .distinctBy { it.id }
                            .sortedByDescending { it.timestamp }
                            .take(totalLimit)
                    }
                } catch (e: Exception) {
                    Log.e("YouTubeRepository", "getVideosForChannels failed: ${e.message}")
                    emptyList()
                }
            }

        /**
         * NEW: Parallel fetch of multiple search queries
         * Executes all queries simultaneously for faster feed generation
         */
        suspend fun parallelSearchQueries(
            queries: List<String>,
            limitPerQuery: Int = 15,
        ): List<Video> =
            withContext(PerformanceDispatcher.networkIO) {
                supervisorScope {
                    val results =
                        queries
                            .map { query ->
                                async(PerformanceDispatcher.networkIO) {
                                    withTimeoutOrNull(10_000L) {
                                        try {
                                            searchVideos(query).first.take(limitPerQuery)
                                        } catch (e: Exception) {
                                            Log.w("YouTubeRepository", "Search query '$query' failed: ${e.message}")
                                            emptyList()
                                        }
                                    } ?: emptyList()
                                }
                            }.awaitAll()

                    results.flatten().distinctBy { it.id }
                }
            }

        /**
         * Fetch a "Lite" Subscription Feed
         * Rotates through subscribed channels to improve fresh-upload coverage.
         */
        suspend fun getSubscriptionFeed(allChannelIds: List<String>): List<Video> =
            withContext(Dispatchers.IO) {
                if (allChannelIds.isEmpty()) return@withContext emptyList()

                val channels =
                    allChannelIds
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()

                if (channels.isEmpty()) return@withContext emptyList()

                val channelsPerRefresh =
                    when {
                        channels.size <= HOME_SUBS_MIN_CHANNELS -> channels.size
                        channels.size <= 60 -> HOME_SUBS_MEDIUM_CHANNELS
                        else -> HOME_SUBS_MAX_CHANNELS
                    }

                val cursor =
                    playerPreferences.homeSubsRotationCursor
                        .first()
                        .coerceIn(0, (channels.size - 1).coerceAtLeast(0))

                val selectedChannels = takeRotatingWindow(channels, cursor, channelsPerRefresh)

                val newCursor = (cursor + selectedChannels.size) % channels.size
                playerPreferences.setHomeSubsRotationCursor(newCursor)

                Log.d(
                    TAG,
                    "Home subs fetch total=${channels.size}, selected=${selectedChannels.size}, cursor=$cursor->$newCursor",
                )

                getVideosForChannels(
                    channelIdsOrUrls = selectedChannels,
                    perChannelLimit = 5,
                    totalLimit = (channelsPerRefresh * 5).coerceAtMost(150),
                )
            }

        /**
         * The web watch response for [videoId], fetched once and shared.
         *
         * Returns null when the request fails; callers fall back to whatever they used before
         * rather than failing the surface.
         */
        suspend fun watchNextResponse(videoId: String): JsonElement? {
            watchNextCache.get(videoId)?.let { return it }
            return watchNextCoalescer.run(videoId) {
                YouTube
                    .watchNextJson(videoId)
                    .getOrNull()
                    ?.also { watchNextCache.put(videoId, it) }
            }
        }

        // Read-only by design: the cache is three deep and holds what the player is working with,
        // so the feed-side callers below reuse an entry but never evict one by populating it.
        private fun cachedWatchMetadata(videoId: String): WatchMetadataResponse? = watchNextCache.get(videoId)?.let(::decodeWatchMetadata)

        /**
         * The typed watch response, off the one request the player already makes.
         *
         * [watchNextResponse] is cached and coalesced; [YouTube.watchMetadata] is neither, and
         * issues its own `/next` (sometimes two). Reaching for that one directly on a cache miss is
         * what had a single load fetching the watch page up to three times, once per consumer, none
         * of them seeding the cache for the next.
         */
        private suspend fun watchMetadataFor(
            videoId: String,
            requireRelated: Boolean = false,
        ): WatchMetadataResponse? {
            cachedWatchMetadata(videoId)?.let { return it }
            val shared = watchNextResponse(videoId)?.let(::decodeWatchMetadata)
            // [YouTube.watchMetadata] retries against the other host when the lane comes back
            // empty, so a caller that needs one still gets that second chance.
            if (shared != null && (!requireRelated || shared.relatedVideos().isNotEmpty())) return shared
            return YouTube.watchMetadata(videoId).getOrNull() ?: shared
        }

        /** Seeds [videoCategory] when a player response happened to carry the category already. */
        fun rememberVideoCategory(
            videoId: String,
            category: String,
        ) {
            videoCategoryCache.remember(videoId, category)
        }

        /**
         * The creator-declared category for [videoId], e.g. "Science & Technology".
         *
         * Costs one small MWEB request, because no client that serves playable URLs returns a
         * microformat and /next carries no category at all. Cached for the process: it is a strong,
         * stable clustering signal for the recommendation engine and never changes.
         */
        suspend fun videoCategory(videoId: String): String? {
            if (videoId.isBlank()) return null
            videoCategoryCache.cached(videoId)?.let { return it }
            return videoCategoryCoalescer.run(videoId) {
                YouTube
                    .videoCategory(videoId)
                    .getOrNull()
                    ?.also { videoCategoryCache.remember(videoId, it) }
            }
        }

        /**
         * The rewatch curve for [videoId], or null when the video has none.
         *
         * Free: it rides the watch response the description and comments already fetch. Plenty of
         * videos have no heatmap — too new, too few views, or live — so null is ordinary.
         */
        suspend fun videoHeatmap(videoId: String): VideoHeatmap? =
            withContext(Dispatchers.IO) {
                VideoHeatmapParser.parse(watchNextResponse(videoId))
            }

        /**
         * [video] filled in from the watch response: exact view and like counts, the upload date and
         * the real description.
         *
         * Read from the cached response where there is one, so the enrichment that used to ride on a
         * second extraction now costs nothing on top of the lane fetch.
         */
        suspend fun enrichFromWatchMetadata(video: Video): Video? =
            withContext(Dispatchers.IO) {
                val response =
                    watchMetadataFor(video.id)
                        ?: return@withContext null
                mergeWatchMetadata(video, response)
            }

        /** The creator's chapters for [videoId], empty when the video has none. */
        suspend fun videoChapters(videoId: String): List<VideoChapter> =
            withContext(Dispatchers.IO) {
                VideoChaptersParser.parse(watchNextResponse(videoId))
            }

        /** The watch page description for [videoId], or null when the response could not be read. */
        suspend fun getVideoDescription(videoId: String): VideoDescriptionPage? =
            withContext(Dispatchers.IO) {
                val response = watchNextResponse(videoId) ?: return@withContext null
                YouTube.videoDescription(response, videoId).takeIf { !it.isEmpty }
            }

        /**
         * The first page of a video's comments, in the order [sortToken] names, or the section's
         * own default when it is null.
         *
         * Falls back to the extractor when InnerTube returns nothing, so a schema change degrades
         * the section instead of emptying it.
         */
        suspend fun getVideoComments(
            videoId: String,
            sortToken: String? = null,
            serviceId: Int = ServiceList.YouTube.serviceId,
        ): CommentsPageResult =
            withContext(Dispatchers.IO) {
                // InnerTube is YouTube's own private web API - it has no notion of other services,
                // so a non-YouTube video goes straight to the generic extractor path below.
                if (serviceId.isYouTubeServiceId) {
                    val token =
                        sortToken
                            ?: watchNextResponse(videoId)?.let(YouTube::commentsContinuation)
                    val page = token?.let { YouTube.comments(it, videoId).getOrNull() }
                    if (page != null && page.comments.isNotEmpty()) {
                        return@withContext CommentsPageResult(
                            comments = page.comments,
                            continuation = page.continuation,
                            sortOptions = page.sortOptions,
                            totalText = page.totalText,
                            totalCount = page.totalCount,
                        )
                    }
                    Log.i(TAG, "InnerTube comments empty for $videoId, falling back to the extractor")
                }
                val (comments, legacyPage) = getComments(videoId, NewPipe.getService(serviceId))
                CommentsPageResult(comments = comments, legacyPage = legacyPage)
            }

        suspend fun getMoreVideoComments(
            videoId: String,
            continuation: String,
            serviceId: Int = ServiceList.YouTube.serviceId,
        ): CommentsPageResult =
            withContext(Dispatchers.IO) {
                if (!serviceId.isYouTubeServiceId) return@withContext CommentsPageResult.EMPTY
                val page = YouTube.comments(continuation, videoId).getOrNull() ?: return@withContext CommentsPageResult.EMPTY
                CommentsPageResult(comments = page.comments, continuation = page.continuation)
            }

        suspend fun getVideoCommentReplies(
            videoId: String,
            continuation: String,
            serviceId: Int = ServiceList.YouTube.serviceId,
        ): CommentsPageResult =
            withContext(Dispatchers.IO) {
                if (!serviceId.isYouTubeServiceId) return@withContext CommentsPageResult.EMPTY
                val page = YouTube.commentReplies(continuation, videoId).getOrNull() ?: return@withContext CommentsPageResult.EMPTY
                CommentsPageResult(comments = page.comments, continuation = page.continuation)
            }

        /**
         * Fetch the first page of comments for a video.
         * Returns the comments and a next-page token (null if no more pages).
         */
        suspend fun getComments(
            videoId: String,
            service: StreamingService = this.service,
        ): Pair<List<Comment>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    val url =
                        if (service.isYouTube) {
                            "https://www.youtube.com/watch?v=$videoId"
                        } else {
                            service.streamLHFactory.getUrl(videoId)
                        }
                    val commentsInfo =
                        org.schabi.newpipe.extractor.comments.CommentsInfo
                            .getInfo(service, url)
                    val comments = mapComments(commentsInfo.relatedItems)
                    Pair(comments, commentsInfo.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        /**
         * Fetch the next page of top-level comments for a video.
         * Returns the new comments and an updated next-page token.
         */
        suspend fun getMoreComments(
            videoId: String,
            nextPage: Page,
            service: StreamingService = this.service,
        ): Pair<List<Comment>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    val url =
                        if (service.isYouTube) {
                            "https://www.youtube.com/watch?v=$videoId"
                        } else {
                            service.streamLHFactory.getUrl(videoId)
                        }
                    val moreItems =
                        org.schabi.newpipe.extractor.comments.CommentsInfo
                            .getMoreItems(service, url, nextPage)
                    val comments = mapComments(moreItems.items)
                    Pair(comments, moreItems.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        /**
         * Fetch replies for a comment
         */
        suspend fun getCommentReplies(
            url: String,
            repliesPage: Page,
            service: StreamingService = this.service,
        ): Pair<List<Comment>, Page?> =
            withContext(Dispatchers.IO) {
                try {
                    val moreItems =
                        org.schabi.newpipe.extractor.comments.CommentsInfo
                            .getMoreItems(service, url, repliesPage)
                    val replies = mapComments(moreItems.items)
                    Pair(replies, moreItems.nextPage)
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    Pair(emptyList(), null)
                }
            }

        private suspend fun mapComments(items: List<CommentsInfoItem>): List<Comment> =
            supervisorScope {
                val embeddedAvatars =
                    items.map { item ->
                        ThumbnailUrlResolver.resolveChannelAvatar(item.uploaderAvatarUrl)
                    }
                val uploaderReferences = items.map { item -> item.uploaderUrl.orEmpty().trim() }
                val missingAvatarReferences =
                    items.indices
                        .asSequence()
                        .filter { index -> embeddedAvatars[index].isBlank() }
                        .map { index -> uploaderReferences[index] }
                        .filter { reference -> reference.isNotBlank() }
                        .distinct()
                        .toList()

                val fallbackAvatars = mutableMapOf<String, String>()
                missingAvatarReferences.chunked(COMMENT_AVATAR_FETCH_CONCURRENCY).forEach { batch ->
                    batch
                        .map { reference ->
                            async(Dispatchers.IO) {
                                val avatar =
                                    runCatching {
                                        withTimeoutOrNull(COMMENT_AVATAR_FETCH_TIMEOUT_MS) {
                                            fetchChannelAvatarById(reference)
                                        }
                                    }.getOrNull().orEmpty()
                                reference to avatar
                            }
                        }.awaitAll()
                        .forEach { (reference, avatar) ->
                            if (avatar.isNotBlank()) fallbackAvatars[reference] = avatar
                        }
                }

                items.mapIndexed { index, item ->
                    val uploaderReference = uploaderReferences[index]
                    Comment(
                        id = item.commentId ?: "",
                        author = item.uploaderName ?: "Unknown",
                        authorThumbnail =
                            selectCommentAuthorThumbnail(
                                embeddedAvatar = embeddedAvatars[index],
                                resolvedChannelAvatar = fallbackAvatars[uploaderReference],
                            ),
                        text = item.commentText.content ?: "",
                        likeCount = item.likeCount.toInt(),
                        publishedTime = item.textualUploadDate ?: "",
                        replyCount = item.replyCount.toInt(),
                        repliesPage = item.replies,
                        isPinned = item.isPinned,
                        authorChannelId = extractChannelId(uploaderReference),
                    )
                }
            }

        /**
         * Fetch playlist details
         */
        suspend fun getPlaylistDetails(
            playlistId: String,
            service: StreamingService = this.service,
        ): io.github.aedev.flow.data.model.Playlist? =
            withContext(Dispatchers.IO) {
                try {
                    val isYouTube = service.isYouTube
                    val playlistUrl =
                        if (isYouTube) {
                            "https://www.youtube.com/playlist?list=$playlistId"
                        } else {
                            service.playlistLHFactory.getUrl(playlistId)
                        }
                    val playlistInfo =
                        org.schabi.newpipe.extractor.playlist.PlaylistInfo
                            .getInfo(service, playlistUrl)

                    val allVideos = mutableListOf<Video>()
                    allVideos +=
                        playlistInfo.relatedItems
                            .filterIsInstance<StreamInfoItem>()
                            .map { it.toVideo(service) }

                    var nextPage = playlistInfo.nextPage
                    while (nextPage != null) {
                        val page =
                            org.schabi.newpipe.extractor.playlist.PlaylistInfo
                                .getMoreItems(service, playlistUrl, nextPage)
                        allVideos +=
                            page.items
                                .filterIsInstance<StreamInfoItem>()
                                .map { it.toVideo(service) }
                        nextPage = page.nextPage
                    }

                    val playlistVideos =
                        if (isYouTube) {
                            val innertubeVideos = fetchInnertubePlaylistVideos(playlistId)
                            if (innertubeVideos.size > allVideos.size) {
                                val knownIds = allVideos.mapTo(HashSet()) { it.id }
                                allVideos + innertubeVideos.filter { it.id !in knownIds }
                            } else {
                                allVideos
                            }
                        } else {
                            allVideos
                        }

                    val bestThumbnail =
                        playlistInfo.thumbnailUrl ?: playlistVideos.firstOrNull()?.thumbnailUrl ?: ""

                    io.github.aedev.flow.data.model.Playlist(
                        id = playlistId,
                        name = playlistInfo.name ?: "Unknown Playlist",
                        thumbnailUrl = bestThumbnail,
                        videoCount = playlistVideos.size,
                        // PipePipeExtractor's PlaylistInfo dropped getDescription() entirely - no
                        // fallback data available, accepted as always-empty for extractor-sourced playlists.
                        description = "",
                        videos = playlistVideos,
                        isLocal = false,
                        serviceId = service.serviceId,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                    null
                }
            }

        /**
         * Helper to extract related videos directly from a StreamInfo object
         * This avoids a redundant network call when we already have the stream info.
         */
        fun getRelatedVideosFromStreamInfo(info: StreamInfo): List<Video> =
            try {
                val infoService = runCatching { NewPipe.getService(info.serviceId) }.getOrDefault(service)
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo(infoService) }
                    .filter { it.id.isNotBlank() }
                    .distinctBy { it.id }
            } catch (e: Exception) {
                emptyList()
            }

        /** Like and dislike counts from the Return YouTube Dislike archive. */
        data class ReturnYouTubeDislikeCounts(
            val likes: Long?,
            val dislikes: Long?,
        )

        /**
         * One request per video id: the player asks for this from both the stream load and the live
         * metadata refresh, and both want the same response.
         */
        suspend fun returnYouTubeDislikeCounts(videoId: String): ReturnYouTubeDislikeCounts? =
            returnYouTubeDislikeCoalescer.run(videoId) {
                val response = YouTube.returnYouTubeDislike(videoId).getOrNull() ?: return@run null
                ReturnYouTubeDislikeCounts(
                    likes = response.likes?.toLong()?.takeIf { it >= 0L },
                    dislikes = response.dislikes?.toLong(),
                )
            }

        data class LiveWatchMetadata(
            val title: String?,
            val channelName: String?,
            val channelId: String?,
            val channelAvatarUrl: String?,
            val subscriberCount: Long?,
            val viewCount: Long?,
            val description: String?,
            val relatedVideos: List<Video>,
        )

        suspend fun getLiveWatchMetadata(videoId: String): LiveWatchMetadata? =
            withContext(Dispatchers.IO) {
                val resp =
                    watchMetadataFor(videoId, requireRelated = true)
                        ?: return@withContext null
                val related = WatchMetadataVideoMapper.relatedVideos(resp)
                Log.i(
                    TAG,
                    "InnerTube watch metadata for $videoId: " +
                        "rawRelated=${resp.relatedResultCount()} parsedRelated=${related.size}",
                )
                LiveWatchMetadata(
                    title = resp.title(),
                    channelName = resp.channelName(),
                    channelId = resp.channelId(),
                    channelAvatarUrl = resp.channelAvatarUrl(),
                    subscriberCount = parseAbbreviatedCount(resp.subscriberCountText()),
                    viewCount = parseAbbreviatedCount(resp.viewCountText()),
                    description = resp.description(),
                    relatedVideos = related,
                )
            }

        /** Light related-video harvest for the feed (InnerTube /next, no stream resolution). */
        suspend fun getRelatedCandidates(
            videoId: String,
            serviceId: Int = ServiceList.YouTube.serviceId,
        ): List<Video> =
            withContext(Dispatchers.IO) {
                if (serviceId.isYouTubeServiceId) {
                    val resp =
                        watchMetadataFor(videoId, requireRelated = true)
                            ?: return@withContext emptyList()
                    enrichLikelyCollabAvatarStacks(WatchMetadataVideoMapper.relatedVideos(resp))
                        .filter { it.id.isNotBlank() && it.id != videoId }
                        .distinctBy { it.id }
                } else {
                    // InnerTube (YouTube.watchMetadata) is YouTube's own private web API - other
                    // services get related videos from the generic extractor's stream page instead.
                    runCatching {
                        val relatedService = NewPipe.getService(serviceId)
                        val info = getVideoStreamInfo(videoId, relatedService) ?: return@runCatching emptyList()
                        getRelatedVideosFromStreamInfo(info)
                            .filter { it.id.isNotBlank() && it.id != videoId }
                            .distinctBy { it.id }
                    }.getOrElse { e ->
                        Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                        emptyList()
                    }
                }
            }

        suspend fun refreshVideoMetadata(video: Video): Video? =
            withContext(Dispatchers.IO) {
                val response = YouTube.watchMetadataLite(video.id).getOrNull() ?: return@withContext null
                mergeWatchMetadata(video, response)
            }

        suspend fun getLiveRelatedVideosBySearch(
            videoId: String,
            title: String?,
            channelName: String?,
        ): List<Video> =
            withContext(Dispatchers.IO) {
                val query =
                    listOfNotNull(
                        channelName?.takeIf { it.isNotBlank() },
                        title?.takeIf { it.isNotBlank() },
                    ).joinToString(" ").takeIf { it.isNotBlank() } ?: return@withContext emptyList()
                val videos =
                    withTimeoutOrNull(8_000L) { searchVideos(query).first }
                        .orEmpty()
                        .filter { it.id.isNotBlank() && it.id != videoId }
                        .distinctBy { it.id }
                        .take(20)
                Log.i(TAG, "Live related search fallback for $videoId: query='$query' results=${videos.size}")
                videos
            }

        suspend fun getLiveWatchMetadataFromNewPipe(videoId: String): LiveWatchMetadata? =
            withContext(Dispatchers.IO) {
                val info = fetchWatchStreamInfoWithAlternates(videoId) ?: return@withContext null
                val thumbnail =
                    info.uploaderAvatars
                        .sortedByDescending { it.height }
                        .firstOrNull()
                        ?.url
                LiveWatchMetadata(
                    title = info.name,
                    channelName = info.uploaderName,
                    channelId = extractChannelId(info.uploaderUrl),
                    channelAvatarUrl = thumbnail,
                    subscriberCount = null,
                    viewCount = info.viewCount.takeIf { it > 0L },
                    description = info.description?.content,
                    relatedVideos =
                        getRelatedVideosFromStreamInfo(info)
                            .filter { it.id != videoId }
                            .distinctBy { it.id },
                )
            }

        private suspend fun fetchWatchStreamInfoWithAlternates(videoId: String): StreamInfo? {
            val urls =
                listOf(
                    "https://www.youtube.com/watch?v=$videoId",
                    "https://youtu.be/$videoId",
                    "https://m.youtube.com/watch?v=$videoId",
                    "https://www.youtube.com/live/$videoId",
                )
            var lastError: Throwable? = null
            urls.forEach { url ->
                val info =
                    try {
                        withTimeoutOrNull(6_000L) { StreamInfo.getInfo(service, url) }
                    } catch (e: Exception) {
                        lastError = e
                        null
                    }
                if (info != null) return info
            }
            Log.w(TAG, "NewPipe watch metadata unavailable for $videoId: ${lastError?.message}")
            return null
        }

        private suspend fun fetchInnertubePlaylistVideos(
            playlistId: String,
            maxContinuationPages: Int = 30,
        ): List<Video> {
            return try {
                val firstPage = YouTube.playlist(playlistId).getOrNull() ?: return emptyList()
                val songs = mutableListOf<SongItem>()
                val seenContinuations = mutableSetOf<String>()

                songs += firstPage.songs
                var continuation = firstPage.songsContinuation ?: firstPage.continuation
                var requestCount = 0

                while (continuation != null && requestCount < maxContinuationPages) {
                    if (!seenContinuations.add(continuation)) break
                    val page = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                    if (page.songs.isEmpty() && page.continuation == null) break
                    songs += page.songs
                    continuation = page.continuation
                    requestCount++
                }

                songs.map { it.toPlaylistVideo() }
            } catch (e: Exception) {
                Log.w(TAG, "Innertube playlist fallback failed for $playlistId: ${e.message}")
                emptyList()
            }
        }

        private fun SongItem.toPlaylistVideo(): Video {
            val artistNames = artists.joinToString(", ") { it.name }
            val channel = artists.firstOrNull()
            return Video(
                id = id,
                title = title,
                channelName = artistNames,
                channelId = channel?.id ?: "",
                thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnail),
                duration = duration ?: 0,
                viewCount = 0,
                uploadDate = "",
                isMusic = false,
            )
        }

        private fun StreamInfoItem.isPaidOrMembersOnly(): Boolean = requiresMembership()

        /**
         * Extension function to convert StreamInfoItem to our Video model
         */
        private fun StreamInfoItem.toVideo(service: StreamingService = this@YouTubeRepository.service): Video {
            val rawUrl = url ?: ""
            val videoId =
                when {
                    rawUrl.contains("watch?v=") -> rawUrl.substringAfter("watch?v=").substringBefore("&")
                    rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
                    rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?")
                    else -> rawUrl.substringAfterLast("/")
                }

            val bestThumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, thumbnailUrl)

            val bestAvatar = uploaderAvatarUrl.orEmpty()

            var durationSecs = if (duration > 0) duration.toInt() else 0

            val isReel = ShortsClassifier.isReel(this)

            if (isReel && durationSecs == 0) {
                durationSecs = 60
            }

            val isLiveStream = streamType == StreamType.LIVE_STREAM
            if (isLiveStream) {
                durationSecs = 0
            }

            // Logic to detect if it's a music video
            val nameLower = name?.lowercase() ?: ""
            val uploaderLower = uploaderName?.lowercase() ?: ""
            val isMusicCandidate =
                uploaderLower.contains("vevo") ||
                    uploaderLower.contains(" - topic") ||
                    nameLower.contains("official music video") ||
                    nameLower.contains("official video") ||
                    nameLower.contains("official audio") ||
                    nameLower.contains("(official)")

            return Video(
                id = videoId,
                title = name ?: "Unknown Title",
                channelName = uploaderName?.takeIf { it.isNotBlank() } ?: "Unknown Channel",
                channelId = extractChannelId(uploaderUrl, service),
                thumbnailUrl = bestThumbnail,
                duration = durationSecs,
                viewCount = viewCount,
                uploadDate =
                    run {
                        val date = uploadDate
                        when {
                            textualUploadDate != null -> {
                                textualUploadDate!!
                            }

                            date != null -> {
                                try {
                                    val d = java.util.Date.from(date.offsetDateTime().toInstant())
                                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    sdf.format(d)
                                } catch (e: Exception) {
                                    "Unknown"
                                }
                            }

                            else -> {
                                "Unknown"
                            }
                        }
                    },
                timestamp =
                    resolveUploadTimestamp(
                        uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli(),
                        textualUploadDate,
                    ),
                channelThumbnailUrl = bestAvatar,
                channelThumbnailUrls = listOfNotNull(uploaderAvatarUrl),
                isUpcoming = streamType == StreamType.NONE,
                isLive = isLiveStream,
                isShort = isReel,
                isMusic = isMusicCandidate,
                serviceId = service.serviceId,
            )
        }

        /**
         * Extension function to convert ChannelInfoItem to our Channel model
         */
        private fun org.schabi.newpipe.extractor.channel.ChannelInfoItem.toChannel(
            service: StreamingService = this@YouTubeRepository.service,
        ): io.github.aedev.flow.data.model.Channel {
            val bestThumbnail = thumbnailUrl ?: ""

            // Extract the channel ID properly from the URL
            val channelId =
                when {
                    url.contains("/channel/") -> url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                    url.contains("/@") -> url.substringAfter("/@").substringBefore("/").substringBefore("?")
                    url.contains("/c/") -> url.substringAfter("/c/").substringBefore("/").substringBefore("?")
                    url.contains("/user/") -> url.substringAfter("/user/").substringBefore("/").substringBefore("?")
                    else -> url.substringAfterLast("/").substringBefore("?")
                }

            return io.github.aedev.flow.data.model.Channel(
                id = channelId,
                name = name ?: "Unknown Channel",
                thumbnailUrl = bestThumbnail,
                subscriberCount = subscriberCount,
                description = description ?: "",
                url = url,
                serviceId = service.serviceId,
            )
        }

        /**
         * Extension function to convert PlaylistInfoItem to our Playlist model
         */
        private fun org.schabi.newpipe.extractor.playlist.PlaylistInfoItem.toPlaylist(
            service: StreamingService = this@YouTubeRepository.service,
        ): io.github.aedev.flow.data.model.Playlist {
            // YouTube playlist urls are "...?list=<id>"; other services (e.g. Bilibili) don't
            // follow that shape, so resolve their id through the service's own link handler
            // instead of assuming a "=" separator.
            val playlistId =
                if (service.isYouTube) {
                    url.substringAfterLast("=")
                } else {
                    resolveNonYouTubePlaylistId(url, service) { url.substringAfterLast("/") }
                }
            val bestThumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(playlistId, thumbnailUrl)

            return io.github.aedev.flow.data.model.Playlist(
                id = playlistId,
                name = name ?: "Unknown Playlist",
                thumbnailUrl = bestThumbnail,
                videoCount = streamCount.toInt(),
                isLocal = false,
                serviceId = service.serviceId,
            )
        }

        private fun extractChannelId(
            uploaderUrl: String?,
            service: StreamingService = this.service,
        ): String {
            if (uploaderUrl.isNullOrBlank()) return ""
            if (!service.isYouTube) {
                // Other services' uploader URLs (e.g. Bilibili's "https://space.bilibili.com/584525428/")
                // don't fit any of the YouTube-shaped patterns below, and a trailing slash makes even
                // the naive last-path-segment fallback return "" - resolve through the service's own
                // link handler instead of guessing at URL structure.
                return resolveNonYouTubeChannelId(uploaderUrl, service) {
                    uploaderUrl.trim().trimEnd('/').substringAfterLast("/").substringBefore("?")
                }
            }
            val url = uploaderUrl.trim()
            return when {
                url.contains("/channel/") -> {
                    url
                        .substringAfter("/channel/")
                        .substringBefore("/")
                        .substringBefore("?")
                }

                url.contains("/@") -> {
                    "@" +
                        url
                            .substringAfter("/@")
                            .substringBefore("/")
                            .substringBefore("?")
                }

                url.contains("/user/") -> {
                    url
                        .substringAfter("/user/")
                        .substringBefore("/")
                        .substringBefore("?")
                }

                url.contains("/c/") -> {
                    url
                        .substringAfter("/c/")
                        .substringBefore("/")
                        .substringBefore("?")
                }

                else -> {
                    url.substringAfterLast("/").substringBefore("?")
                }
            }
        }

        private fun resolveUploadTimestamp(
            absoluteMillis: Long?,
            textualDate: String?,
        ): Long {
            absoluteMillis?.let { if (it > 0L) return it }
            // Shared parser: the old private copy carried the plural-"s" bug
            // ("3 days" matched the seconds branch), which stamped every
            // plural-dated subs video as seconds old — stale uploads then won
            // the recency sort and the fresh-subs slots over genuinely new ones.
            val parsed = RelativeUploadDateParser.parse(textualDate)
            return parsed ?: System.currentTimeMillis()
        }

        private fun <T> takeRotatingWindow(
            items: List<T>,
            start: Int,
            count: Int,
        ): List<T> {
            if (items.isEmpty() || count <= 0) return emptyList()
            if (items.size <= count) return items

            val safeStart = start.coerceIn(0, items.lastIndex)
            val result = ArrayList<T>(count)
            for (i in 0 until count) {
                val index = (safeStart + i) % items.size
                result.add(items[index])
            }
            return result
        }

        companion object {
            private const val TAG = "YouTubeRepository"
            private const val HOME_SUBS_MIN_CHANNELS = 10
            private const val HOME_SUBS_MEDIUM_CHANNELS = 14
            private const val HOME_SUBS_MAX_CHANNELS = 18
            private const val COMMENT_AVATAR_FETCH_CONCURRENCY = 4
            private const val COMMENT_AVATAR_FETCH_TIMEOUT_MS = 6_000L
            private const val REEL_INDEX_TIMEOUT_MS = 3_000L
            private const val WATCH_NEXT_CACHE_SIZE = 3
            private const val VIDEO_CATEGORY_CACHE_SIZE = 500

            @Volatile
            private var instance: YouTubeRepository? = null

            fun getInstance(
                playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences,
                channelReelIndex: ChannelReelIndex,
            ): YouTubeRepository =
                instance ?: synchronized(this) {
                    instance ?: YouTubeRepository(playerPreferences, channelReelIndex).also { instance = it }
                }

            fun getInstance(): YouTubeRepository =
                instance ?: error("YouTubeRepository not initialized. Call getInstance(playerPreferences) first.")
        }
    }

internal fun selectCommentAuthorThumbnail(
    embeddedAvatar: String?,
    resolvedChannelAvatar: String?,
): String =
    ThumbnailUrlResolver
        .resolveChannelAvatar(embeddedAvatar)
        .ifBlank { ThumbnailUrlResolver.resolveChannelAvatar(resolvedChannelAvatar) }

internal fun mergeWatchMetadata(
    video: Video,
    response: WatchMetadataResponse,
): Video? {
    val uploadDate = response.uploadDate()?.takeIf { it.isNotBlank() } ?: return null
    // The relative form first: the absolute one is a date with no time, so on its own it places
    // every upload at midnight and reads back as however long the day has been running.
    val timestamp =
        response.relativeUploadDate()?.let { parseRelativeToTimestamp(it) }
            ?: parseToTimestamp(uploadDate)
            ?: video.timestamp
    val avatarUrl = response.channelAvatarUrl().orEmpty().ifBlank { video.channelThumbnailUrl }
    return video.copy(
        title = response.title().orEmpty().ifBlank { video.title },
        channelName = response.channelName().orEmpty().ifBlank { video.channelName },
        channelId = response.channelId().orEmpty().ifBlank { video.channelId },
        viewCount = parseAbbreviatedCount(response.viewCountText()) ?: video.viewCount,
        likeCount = parseAbbreviatedCount(response.likeCountText()) ?: video.likeCount,
        uploadDate = uploadDate,
        timestamp = timestamp,
        description = response.description().orEmpty().ifBlank { video.description },
        channelThumbnailUrl = avatarUrl,
        channelThumbnailUrls =
            if (avatarUrl.isNotBlank()) {
                (listOf(avatarUrl) + video.channelThumbnailUrls).distinct()
            } else {
                video.channelThumbnailUrls
            },
    )
}

internal fun parseAbbreviatedCount(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    val match = Regex("""([\d.,]+)\s*([KkMmBb])?""").find(text) ?: return null
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val mult =
        when (match.groupValues[2].lowercase(Locale.US)) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
    return (number * mult).toLong()
}

internal fun parseDurationTextToSeconds(text: String?): Int {
    if (text.isNullOrBlank()) return 0
    val parts = text.split(":").mapNotNull { it.trim().toIntOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}

internal fun String?.isLiveViewCountText(): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase(Locale.US)
    return lower.contains("watching") || lower.contains("viewer")
}

/**
 * Process-lifetime memo for video categories. Separate from the repository so the keep/skip rules
 * can be exercised without standing one up; a category never changes, so there is no TTL.
 */
internal class VideoCategoryMemo(
    private val maxEntries: Int = 500,
) {
    // A plain access-ordered map rather than android.util.LruCache: that one is an Android stub in
    // a JVM test and silently returns null, which would make these rules untestable.
    private val entries =
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > maxEntries
        }

    @Synchronized
    fun cached(videoId: String): String? = videoId.takeIf { it.isNotBlank() }?.let(entries::get)

    @Synchronized
    fun remember(
        videoId: String,
        category: String,
    ) {
        if (videoId.isBlank() || category.isBlank()) return
        entries[videoId] = category
    }
}

private val watchMetadataJson = Json { ignoreUnknownKeys = true }

/** The watch response as the typed model, or null when the payload no longer matches it. */
internal fun decodeWatchMetadata(raw: JsonElement): WatchMetadataResponse? =
    runCatching { watchMetadataJson.decodeFromJsonElement(WatchMetadataResponse.serializer(), raw) }.getOrNull()

internal object WatchMetadataVideoMapper {
    fun relatedVideos(resp: WatchMetadataResponse): List<Video> =
        resp.relatedVideos().mapNotNull { cv ->
            val id = cv.videoId ?: return@mapNotNull null
            val viewText = cv.viewCountText?.text()
            val isLive = cv.isLive || viewText.isLiveViewCountText()
            val uploadDateText = cv.publishedTimeText?.text() ?: ""
            Video(
                id = id,
                title = cv.title?.text() ?: "",
                channelName = cv.longBylineText?.text() ?: "",
                channelId = cv.channelId().orEmpty(),
                thumbnailUrl =
                    cv.thumbnail?.bestUrl()?.let { ThumbnailUrlResolver.normalizeVideoThumbnail(id, it) }
                        ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(id),
                channelThumbnailUrl =
                    cv.channelAvatarUrl?.let(ThumbnailUrlResolver::resolveChannelAvatar).orEmpty(),
                duration = if (isLive) 0 else parseDurationTextToSeconds(cv.lengthText?.text()),
                viewCount = parseAbbreviatedCount(viewText) ?: 0L,
                uploadDate = uploadDateText,
                // Video.timestamp defaults to now(), which made every related item
                // look brand new — defeating the age filter and shorts-shelf sort.
                // Parse the real age; 0 means unknown (callers fall back to text).
                timestamp = RelativeUploadDateParser.parse(uploadDateText) ?: 0L,
                isLive = isLive,
            )
        }
}
