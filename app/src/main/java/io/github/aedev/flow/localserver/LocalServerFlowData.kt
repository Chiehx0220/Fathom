package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SearchHistoryRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video as FlowVideo
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.explore.chartsCountryOrFallback
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ChannelReelIndex
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
import io.github.aedev.flow.ui.screens.home.FeedTasteProfile
import io.github.aedev.flow.ui.screens.home.GraphCandidate
import io.github.aedev.flow.ui.screens.home.HomeFeedSources
import io.github.aedev.flow.ui.screens.home.assembleHomeFeed
import io.github.aedev.flow.ui.screens.home.buildHomeFeedLanes
import io.github.aedev.flow.ui.screens.home.demoteByFit
import io.github.aedev.flow.ui.screens.home.dynamicFreshSubSlots
import io.github.aedev.flow.ui.screens.home.enrichAvatars
import io.github.aedev.flow.ui.screens.home.feedTasteProfile
import io.github.aedev.flow.ui.screens.home.filterValid
import io.github.aedev.flow.ui.screens.home.filterWatched
import io.github.aedev.flow.ui.screens.home.spaceByChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.schabi.newpipe.extractor.InfoItem
import io.github.aedev.flow.bilibili.BilibiliLink
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Reads/writes Flow's native storage directly - no bridge interface, no separate copy. Adapts
 * bare-ID-keyed models to the URL-keyed [InfoItem] shape Local Server's rendering expects.
 * `runBlocking` throughout: handlers run on a background thread pool, never a coroutine.
 */

private fun HistoryDbHelper.subscriptionRepository() = SubscriptionRepository.getInstance(appContext)

private fun HistoryDbHelper.viewHistory() = ViewHistory.getInstance(appContext)

/** Subscriptions as [InfoItem]s. */
fun HistoryDbHelper.nativeSubscriptions(): List<InfoItem> = runBlocking {
    subscriptionRepository().getAllSubscriptions().first().map { sub ->
        val item = ChannelInfoItem(sub.serviceId, channelIdToUrl(sub.channelId, sub.serviceId), sub.channelName)
        if (sub.channelThumbnail.isNotEmpty()) {
            item.thumbnailUrl = sub.channelThumbnail
        }
        item
    }
}

fun HistoryDbHelper.nativeIsSubscribed(channelUrl: String?): Boolean {
    val channelId = channelUrlToId(channelUrl) ?: return false
    return runBlocking { subscriptionRepository().isSubscribed(channelId).first() }
}

/** Subscribes, or refreshes name/avatar if already subscribed - never resets tracked state. */
fun HistoryDbHelper.nativeAddSubscription(channelUrl: String, channelName: String?, channelAvatar: String?) {
    val channelId = channelUrlToId(channelUrl) ?: return
    val serviceId =
        if (BilibiliLink.isBilibili(channelUrl)) {
            LocalServerBilibili.serviceId
        } else {
            runCatching { NewPipe.getServiceByUrl(channelUrl).serviceId }.getOrDefault(0)
        }
    runBlocking {
        subscriptionRepository().subscribeOrUpdateInfo(channelId, channelName ?: "", channelAvatar ?: "", serviceId)
    }
}

fun HistoryDbHelper.nativeRemoveSubscription(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking { subscriptionRepository().unsubscribe(channelId) }
}

/** Reads/writes FlowNeuroEngine's block list directly - ranking already excludes blocked
 * channels, so this applies to native recommendations too, not just Local Server. */
fun HistoryDbHelper.nativeIsChannelBlocked(channelUrl: String?): Boolean {
    val channelId = channelUrlToId(channelUrl) ?: return false
    return runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.getInstance(appContext).getBlockedChannels().contains(channelId)
    }
}

fun HistoryDbHelper.nativeBlockedChannelIds(): Set<String> = runBlocking {
    ensureFlowNeuroInitialized()
    FlowNeuroEngine.getInstance(appContext).getBlockedChannels()
}

fun HistoryDbHelper.nativeBlockChannel(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.blockChannel(channelId)
    }
}

fun HistoryDbHelper.nativeUnblockChannel(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.unblockChannel(channelId)
    }
}

/** Bare video IDs the user has watched, as full watch URLs - matches old `getWatchedUrls()` shape. */
fun HistoryDbHelper.nativeWatchedUrls(): Set<String> = runBlocking {
    viewHistory().getAllWatchedVideoIdentities().map { videoIdToUrl(it.videoId, it.serviceId) }.toSet()
}

private fun HistoryDbHelper.playlistRepository() = PlaylistRepository(appContext)

/** Playlist bookmarks ("save this YouTube playlist") and Watch Later both live in Flow's native
 * PlaylistRepository (Room) - Watch Later is a hardcoded internal playlist there, the same table a
 * playlist saved from Flow's own UI lands in. */
fun HistoryDbHelper.nativeIsPlaylistBookmarked(playlistUrl: String?): Boolean {
    val playlistId = playlistUrlToId(playlistUrl) ?: return false
    return runBlocking { playlistRepository().isExternalPlaylistSaved(playlistId) }
}

fun HistoryDbHelper.nativeBookmarkPlaylist(playlistUrl: String, name: String?, thumbnailUrl: String?) {
    val playlistId = playlistUrlToId(playlistUrl) ?: return
    runBlocking { playlistRepository().saveExternalVideoPlaylist(playlistId, name ?: "", "", thumbnailUrl ?: "") }
}

fun HistoryDbHelper.nativeUnbookmarkPlaylist(playlistUrl: String) {
    val playlistId = playlistUrlToId(playlistUrl) ?: return
    runBlocking { playlistRepository().unsaveExternalPlaylist(playlistId) }
}

/** Bookmarked playlists as [InfoItem]s. No uploader field (`PlaylistEntity` has none) -
 * `renderGrid()` uses `typeBadge` for that slot on playlist cards regardless. */
fun HistoryDbHelper.nativeBookmarkedPlaylists(): List<InfoItem> = runBlocking {
    playlistRepository().getSavedVideoPlaylistsFlow().first().map { info ->
        val item = PlaylistInfoItem(0, playlistIdToUrl(info.id), info.name)
        if (info.thumbnailUrl.isNotEmpty()) {
            item.thumbnailUrl = info.thumbnailUrl
        }
        item
    }
}

fun HistoryDbHelper.nativeIsWatchLater(videoUrl: String): Boolean {
    val videoId = LocalHttpServer.getVideoId(videoUrl)
    if (videoId.isEmpty()) return false
    return runBlocking { playlistRepository().isInWatchLater(videoId) }
}

/** HTTP action params carry no duration/viewCount/uploadDate - only the triggering button's own
 * markup. Uses the same `-1`/empty convention as [StreamInfoItem.toFlowVideo] for missing data. */
private fun buildFlowVideoFromParams(videoId: String, title: String, uploader: String, thumbnailUrl: String?, uploaderUrl: String?) = FlowVideo(
    id = videoId,
    title = title,
    channelName = uploader,
    channelId = channelUrlToId(uploaderUrl) ?: "",
    thumbnailUrl = thumbnailUrl ?: "",
    duration = 0,
    viewCount = -1,
    uploadDate = "",
)

/** Also reports a SAVED signal to FlowNeuroEngine, mirroring native's
 * `QuickActionsViewModel.toggleWatchLater` - best-effort, same as [reportFlowNeuroInteraction]. */
fun HistoryDbHelper.nativeAddWatchLater(url: String, title: String, uploader: String, thumbnailUrl: String?, uploaderUrl: String?) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    val video = buildFlowVideoFromParams(videoId, title, uploader, thumbnailUrl, uploaderUrl)
    runBlocking {
        playlistRepository().addToWatchLater(video)
        ensureFlowNeuroInitialized()
        try {
            FlowNeuroEngine.onVideoInteraction(video, InteractionType.SAVED)
        } catch (e: Exception) {
            LocalHttpServer.log("FlowNeuro SAVED signal error: " + e.message)
        }
    }
}

fun HistoryDbHelper.nativeRemoveWatchLater(url: String) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking { playlistRepository().removeFromWatchLater(videoId) }
}

/** Watch Later items as [InfoItem]s. Video-only, matching `renderWatchLaterButton()`'s call sites. */
fun HistoryDbHelper.nativeWatchLaterItems(): List<InfoItem> = runBlocking {
    playlistRepository().getVideoOnlyWatchLaterFlow().first().map { it.toStreamInfoItem(0) }
}

private fun HistoryDbHelper.likedVideosRepository() = LikedVideosRepository.getInstance(appContext)

/** Like/dislike state via `LikedVideosRepository` (DataStore), same store as native's
 * `VideoPlayerViewModel.likeVideo/dislikeVideo`. Same `-1`/empty convention as
 * [nativeAddWatchLater] for fields HTTP params don't carry. */
fun HistoryDbHelper.nativeLikeState(videoUrl: String): String? {
    val videoId = LocalHttpServer.getVideoId(videoUrl)
    if (videoId.isEmpty()) return null
    return runBlocking { likedVideosRepository().getLikeState(videoId).first() }
}

fun HistoryDbHelper.nativeLikeVideo(url: String, title: String, uploader: String, thumbnailUrl: String?, uploaderUrl: String?, serviceId: Int) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        likedVideosRepository().likeVideo(
            LikedVideoInfo(videoId = videoId, title = title, thumbnail = thumbnailUrl ?: "", channelName = uploader, serviceId = serviceId),
        )
        reportRatingSignal(videoId, title, uploader, thumbnailUrl, uploaderUrl, InteractionType.LIKED)
    }
}

fun HistoryDbHelper.nativeDislikeVideo(url: String, title: String, uploader: String, thumbnailUrl: String?, uploaderUrl: String?) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        likedVideosRepository().dislikeVideo(videoId)
        reportRatingSignal(videoId, title, uploader, thumbnailUrl, uploaderUrl, InteractionType.DISLIKED)
    }
}

fun HistoryDbHelper.nativeRemoveLikeState(url: String) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking { likedVideosRepository().removeLikeState(videoId) }
}

private suspend fun HistoryDbHelper.reportRatingSignal(
    videoId: String, title: String, uploader: String, thumbnailUrl: String?, uploaderUrl: String?, type: InteractionType,
) {
    ensureFlowNeuroInitialized()
    try {
        val video = buildFlowVideoFromParams(videoId, title, uploader, thumbnailUrl, uploaderUrl)
        FlowNeuroEngine.onVideoInteraction(video, type)
    } catch (e: Exception) {
        LocalHttpServer.log("FlowNeuro $type signal error: " + e.message)
    }
}

private fun HistoryDbHelper.searchHistoryRepository() = SearchHistoryRepository(appContext)

/** `SearchHistoryRepository` (DataStore), same store as native's search bar. HTTP contract is a
 * plain query-string list, so [nativeDeleteSearchQuery] resolves id by query text. */
fun HistoryDbHelper.nativeAddSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking { searchHistoryRepository().saveSearchQuery(query.trim()) }
}

fun HistoryDbHelper.nativeSearchHistory(): List<String> = runBlocking {
    searchHistoryRepository().getRecentSearches(10).map { it.query }
}

fun HistoryDbHelper.nativeDeleteSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking {
        val repo = searchHistoryRepository()
        val item = repo.getSearchHistoryFlow().first().find { it.query == query }
        if (item != null) repo.deleteSearchItem(item.id)
    }
}

private fun HistoryDbHelper.playerPreferences() = PlayerPreferences(appContext)

/** `PlayerPreferences` (DataStore). `hideWatched` maps to native's home-feed toggle (Local Server
 * has one combined feed, not separate home/subscriptions screens). `videoQuality` maps to the
 * Wi-Fi quality slot (LAN-only, cellular slot never applies). */
fun HistoryDbHelper.nativeHideWatched(): Boolean = runBlocking { playerPreferences().hideWatchedVideosFromHome.first() }

fun HistoryDbHelper.nativeSetHideWatched(enabled: Boolean) {
    runBlocking { playerPreferences().setHideWatchedVideosFromHome(enabled) }
}

fun HistoryDbHelper.nativeHideShorts(): Boolean = runBlocking { !playerPreferences().shortsContentEnabled.first() }

fun HistoryDbHelper.nativeSetHideShorts(hide: Boolean) {
    runBlocking { playerPreferences().setShortsContentEnabled(!hide) }
}

fun HistoryDbHelper.nativeVideoQuality(): String = runBlocking { playerPreferences().defaultQualityWifi.first().label }

/**
 * Create-or-touch a history entry's metadata without disturbing a saved playback position.
 * `uploaderAvatar` is dropped - no column for it in native's history table, and native's history
 * screen doesn't render one either.
 */
fun HistoryDbHelper.nativeSaveToHistory(
    title: String,
    url: String,
    uploader: String,
    thumbnailUrl: String,
    serviceId: Int,
    uploaderUrl: String?,
    @Suppress("UNUSED_PARAMETER") uploaderAvatar: String?,
) {
    val videoId = LocalHttpServer.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        viewHistory().touchHistoryEntry(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = uploader,
            channelId = channelUrlToId(uploaderUrl) ?: "",
            serviceId = serviceId,
        )
    }
}

/** Updates only the progress columns (position/duration) - never clobbers title/thumbnail/channel. */
fun HistoryDbHelper.nativeUpdateWatchProgress(videoUrl: String, percentWatched: Int, durationSeconds: Int) {
    val videoId = LocalHttpServer.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    val durationMs = durationSeconds.toLong() * 1000
    val positionMs = (durationMs * percentWatched.coerceIn(0, 100)) / 100
    runBlocking { viewHistory().updatePlaybackProgress(videoId, positionMs, durationMs) }
}

/** History rows as [InfoItem]s. */
fun HistoryDbHelper.nativeHistory(): List<InfoItem> = runBlocking {
    viewHistory().getAllHistory().first().map { entry ->
        val item = StreamInfoItem(entry.serviceId, videoIdToUrl(entry.videoId, entry.serviceId), entry.title, StreamType.VIDEO_STREAM)
        item.setUploaderName(entry.channelName)
        item.setUploaderUrl(if (entry.channelId.isNotEmpty()) channelIdToUrl(entry.channelId, entry.serviceId) else "")
        if (entry.thumbnailUrl.isNotEmpty()) {
            item.thumbnailUrl = entry.thumbnailUrl
        }
        item
    }
}

fun HistoryDbHelper.nativeRemoveFromHistory(videoUrl: String) {
    val videoId = LocalHttpServer.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().clearVideoHistory(videoId) }
}

fun HistoryDbHelper.nativeClearHistory() {
    runBlocking { viewHistory().clearAllHistory() }
}

/**
 * Home-feed candidates/ranking come from native `YouTubeRepository`/`FlowNeuroEngine` directly,
 * not a ported copy. `toFlowVideo()`/`toStreamInfoItem()` convert at the boundary between
 * NewPipeExtractor's [InfoItem] hierarchy (Local Server's native type across every page) and
 * Flow's video-only [FlowVideo] display model.
 */

// Singleton shared with native (same instance as the Hilt provider).
private fun HistoryDbHelper.youTubeRepository(): YouTubeRepository =
    YouTubeRepository.getInstance(PlayerPreferences(appContext), ChannelReelIndex())

// Singleton reached through LocalServerEntryPoint, since this runs outside Hilt.
private fun HistoryDbHelper.homeFeedSources(): HomeFeedSources =
    localServerEntryPoint(appContext).homeFeedSources()

@Volatile
private var flowNeuroInitialized = false

private suspend fun HistoryDbHelper.ensureFlowNeuroInitialized() {
    if (flowNeuroInitialized) return
    FlowNeuroEngine.initialize(appContext)
    flowNeuroInitialized = true
}

// serviceId is unused: this.serviceId is authoritative; the parameter keeps the converters symmetric.

/**
 * Listing candidates (StreamInfoItem) carry no tags/description - only the watch-page StreamInfo
 * overload below has those.
 */
fun StreamInfoItem.toFlowVideo(@Suppress("UNUSED_PARAMETER") serviceId: Int): FlowVideo {
    val durationSeconds = this.duration.coerceAtLeast(0).toInt()
    return FlowVideo(
        id = LocalHttpServer.getVideoId(this.url),
        title = this.name ?: "",
        channelName = this.uploaderName ?: this.name ?: "",
        channelId = channelUrlToId(this.uploaderUrl) ?: "",
        thumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.thumbnailUrl),
        duration = durationSeconds,
        viewCount = this.viewCount.coerceAtLeast(-1),
        likeCount = 0,
        uploadDate = this.textualUploadDate ?: "",
        description = "",
        channelThumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.uploaderAvatarUrl),
        tags = emptyList(),
        isLive = this.streamType == StreamType.LIVE_STREAM || this.streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = durationSeconds in 1..120,
        serviceId = this.serviceId,
    )
}

/** Full watch-page detail (StreamInfo) - includes description/tags, unlike StreamInfoItem. */
fun StreamInfo.toFlowVideo(@Suppress("UNUSED_PARAMETER") serviceId: Int): FlowVideo {
    val durationSeconds = this.duration.coerceAtLeast(0).toInt()
    return FlowVideo(
        id = LocalHttpServer.getVideoId(this.url),
        title = this.name ?: "",
        channelName = this.uploaderName ?: "",
        channelId = channelUrlToId(this.uploaderUrl) ?: "",
        thumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.thumbnails),
        duration = durationSeconds,
        viewCount = this.viewCount.coerceAtLeast(-1),
        likeCount = this.likeCount.coerceAtLeast(0),
        uploadDate = this.textualUploadDate ?: "",
        description = this.description?.content ?: "",
        channelThumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.uploaderAvatars),
        tags = this.tags ?: emptyList(),
        isLive = this.streamType == StreamType.LIVE_STREAM || this.streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = durationSeconds in 1..120,
        serviceId = this.serviceId,
    )
}

/** Converts [FlowVideo] to the [InfoItem] shape `HtmlRenderer*` expects. */
fun FlowVideo.toStreamInfoItem(serviceId: Int): StreamInfoItem {
    val item = StreamInfoItem(serviceId, videoIdToUrl(this.id, serviceId), this.title, StreamType.VIDEO_STREAM)
    item.setUploaderName(this.channelName)
    item.setUploaderUrl(if (this.channelId.isNotEmpty()) channelIdToUrl(this.channelId, serviceId) else "")
    if (this.thumbnailUrl.isNotEmpty()) {
        item.thumbnailUrl = this.thumbnailUrl
    }
    if (this.channelThumbnailUrl.isNotEmpty()) {
        item.uploaderAvatarUrl = this.channelThumbnailUrl
    }
    item.setDuration(this.duration.toLong())
    item.setViewCount(this.viewCount)
    item.setTextualUploadDate(this.uploadDate)
    if (this.description.isNotEmpty()) item.setShortDescription(this.description)
    return item
}

// 5-minute cache of the assembled home feed, keyed by serviceId + feedMode.
private const val HOME_FEED_CACHE_TTL_MS = 5 * 60 * 1000L
private data class HomeFeedCacheEntry(val items: List<InfoItem>, val timestampMs: Long)
private val homeFeedCache = java.util.concurrent.ConcurrentHashMap<String, HomeFeedCacheEntry>()

/** One round of FlowNeuro discovery queries, fetched concurrently. `resetDepth`: fresh session
 * vs. load-more. Query count matches native's wave-1. Shared by [buildAndRankHomeFeed] and
 * [continueDiscoveryFeed]. */
private suspend fun CoroutineScope.fetchDiscoveryVideos(repo: YouTubeRepository, resetDepth: Boolean): List<FlowVideo> =
    runCatching {
        val queries = FlowNeuroEngine.generateDiscoveryQueries(resetDepth = resetDepth)
        queries.take(3).map { query ->
            async { runCatching { repo.searchVideos(query).first }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
    }.getOrDefault(emptyList())

/** Shared inputs for the home-feed builders: watched-video gating (`hideWatchedVideosFromHome`),
 * FlowNeuroEngine block/suppress lists, brain snapshot + taste profile, subscribed-channel
 * avatars. */
private data class HomeFeedContext(
    val watched: Set<String>,
    val excludedChannels: Set<String>,
    val brain: UserBrain,
    val taste: FeedTasteProfile,
    val subAvatarMap: Map<String, String>,
)

private suspend fun HistoryDbHelper.buildHomeFeedContext(): HomeFeedContext {
    val hideWatched = playerPreferences().hideWatchedVideosFromHome.first()
    val watched = if (hideWatched) viewHistory().getAllWatchedVideoIds() else emptySet()
    val excludedChannels = runCatching { FlowNeuroEngine.getExcludedChannelIds() }.getOrDefault(emptySet())
    val brain = runCatching { FlowNeuroEngine.getBrainSnapshot() }.getOrElse { UserBrain() }
    val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))
    val subAvatarMap = runCatching {
        subscriptionRepository().getAllSubscriptions().first()
            .filter { it.channelThumbnail.isNotEmpty() }
            .associate { it.channelId to it.channelThumbnail }
    }.getOrDefault(emptyMap())
    return HomeFeedContext(watched, excludedChannels, brain, taste, subAvatarMap)
}

/** Dedupes by video id, ranks via FlowNeuroEngine (falls back to unranked order on failure),
 * converts to [StreamInfoItem]. Used by [continueDiscoveryFeed]. */
private suspend fun HistoryDbHelper.rankAndConvert(videos: List<FlowVideo>, serviceId: Int): List<StreamInfoItem> {
    val deduped = LinkedHashMap<String, FlowVideo>()
    // Only this page's serviceId: watch and channel links are built for one service.
    for (v in videos) if (v.id.isNotBlank() && v.serviceId == serviceId) deduped.putIfAbsent(v.id, v)
    if (deduped.isEmpty()) return emptyList()

    val subIds = subscriptionRepository().getAllSubscriptionIds()
    val ranked = runCatching { FlowNeuroEngine.rank(deduped.values.toList(), subIds) }
        .getOrDefault(deduped.values.toList())
    return ranked.map { it.toStreamInfoItem(serviceId) }
}

/**
 * Assembles/ranks the home feed via the SAME pipeline as native's wave-1: [buildHomeFeedLanes]/
 * [assembleHomeFeed] from `io.github.aedev.flow.ui.screens.home`, not a ported copy. Gets
 * quota-balanced source blending, same-channel spacing, duration/format-fit demotion,
 * fresh-upload pinning, and the related-video (/next graph) lane.
 *
 * UPSTREAM COUPLING: [buildHomeFeedLanes], [assembleHomeFeed], [feedTasteProfile], [filterValid],
 * [filterWatched], [demoteByFit], [spaceByChannel], [dynamicFreshSubSlots], [enrichAvatars] are
 * upstream-owned (`HomeFeedAssembly.kt`/`HomeFeedFilters.kt`/`HomeFeedRanking.kt`, PR #1040). A
 * signature/behavior change there on upstream sync won't surface as a merge conflict (different
 * file) - re-verify this function compiles and the home feed renders after every sync.
 *
 * `rssFeed`/`homeFeedSources()`/`youTubeRepository()` resolve to the SAME Hilt singletons native
 * Home uses (via [localServerEntryPoint]/[YouTubeRepository.getInstance]), not separate instances.
 *
 * Deliberate divergence from native:
 * - YouTube-only; no Bilibili lane.
 * - No wave-2 enrichment / persistent feed cache / reserve page (those smooth a long-lived
 *   Compose screen, not a single request/response cycle).
 *
 * Second return value: whether [continueDiscoveryFeed] has more to fetch. No NewPipeExtractor
 * Page involved - `YoutubeTrendingExtractor` has no continuation, so "load more" is a deeper
 * discovery-query round.
 */
fun HistoryDbHelper.buildAndRankHomeFeed(serviceId: Int, feedMode: String): Pair<List<InfoItem>, Boolean> = runBlocking {
    val cacheKey = "$serviceId:$feedMode"
    val cached = homeFeedCache[cacheKey]
    val now = System.currentTimeMillis()
    if (cached != null && now - cached.timestampMs < HOME_FEED_CACHE_TTL_MS) {
        return@runBlocking cached.items to cached.items.isNotEmpty()
    }

    ensureFlowNeuroInitialized()
    val repo = youTubeRepository()
    val sources = homeFeedSources()
    val subIds = subscriptionRepository().getAllSubscriptionIds()
    val context = buildHomeFeedContext()
    val cacheFilters: suspend () -> HomeFeedCacheFilters = {
        HomeFeedCacheFilters(
            watchedVideoIds = context.watched,
            suppressedVideoIds = context.brain.suppressedVideoIds.keys,
            blockedChannelIds = context.brain.blockedChannels,
            suppressedChannelIds = context.brain.suppressedChannels.keys,
        )
    }

    lateinit var rawSubs: List<FlowVideo>
    lateinit var rawDiscovery: List<FlowVideo>
    lateinit var rawViral: List<FlowVideo>
    lateinit var rawRelated: List<GraphCandidate>
    lateinit var rssFeed: List<FlowVideo>
    supervisorScope {
        val subsDeferred = async {
            if (feedMode != "mix" || subIds.isEmpty()) return@async emptyList()
            runCatching { repo.getSubscriptionFeed(subIds.toList()) }.getOrDefault(emptyList())
        }
        val discoveryDeferred = async { fetchDiscoveryVideos(repo, resetDepth = true) }
        val viralDeferred = async { runCatching { trendingVideos() }.getOrDefault(emptyList()) }
        val relatedDeferred = async {
            runCatching {
                val seedInputs = sources.historySeedInputs()
                val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs)
                sources.fetchRelatedGraph(seedInputs, seedIds, cacheFilters).candidates
            }.getOrDefault(emptyList())
        }
        // Cache read from the same SubscriptionFeedRepository as native; no network call.
        val rssDeferred = async {
            runCatching {
                localServerEntryPoint(appContext).subscriptionFeedRepository().observeFeed().first()
            }.getOrDefault(emptyList())
        }

        rawSubs = subsDeferred.await()
        rawDiscovery = discoveryDeferred.await()
        rawViral = viralDeferred.await()
        rawRelated = relatedDeferred.await()
        rssFeed = rssDeferred.await()
    }

    val lanes = buildHomeFeedLanes(
        rawSubs = rawSubs,
        rawDiscovery = rawDiscovery,
        rawViral = rawViral,
        rawRelated = rawRelated,
        rssFeed = rssFeed,
        watched = context.watched,
        excludedChannels = context.excludedChannels,
        taste = context.taste,
        now = now,
        freshSlotTarget = dynamicFreshSubSlots(subIds.size),
        subAvatarMap = context.subAvatarMap,
        rank = { pool -> runCatching { FlowNeuroEngine.rank(pool, subIds) }.getOrDefault(pool) },
    )
    val mix = assembleHomeFeed(
        lanes = lanes,
        onScreenIds = emptySet(),
        subCount = subIds.size,
        totalInteractions = context.brain.totalInteractions,
    )

    // Subscriptions and history span all services; filter to this page's serviceId first (ServiceIdHelpers.kt).
    val result = mix.videos.filter { it.serviceId == serviceId }.map { it.toStreamInfoItem(serviceId) }
    if (result.isEmpty()) return@runBlocking emptyList<InfoItem>() to false

    homeFeedCache[cacheKey] = HomeFeedCacheEntry(result, now)
    result to true
}

/** `feedMode == "subs"`: native subscription pool via `filterValid`/`filterWatched`/`demoteByFit`/
 * `spaceByChannel`, but NOT through [buildHomeFeedLanes]/[assembleHomeFeed] - that pipeline caps
 * the subs lane at 15 and the mix at 40 (`HOME_TARGET_SIZE`), which would truncate this mode's
 * uncapped "just my subscriptions" contract. Deliberate, not an oversight. */
fun HistoryDbHelper.buildSubsOnlyFeed(serviceId: Int): List<InfoItem> = runBlocking {
    val cacheKey = "$serviceId:subs"
    val cached = homeFeedCache[cacheKey]
    val now = System.currentTimeMillis()
    if (cached != null && now - cached.timestampMs < HOME_FEED_CACHE_TTL_MS) {
        return@runBlocking cached.items
    }

    ensureFlowNeuroInitialized()
    val subIds = subscriptionRepository().getAllSubscriptionIds()
    if (subIds.isEmpty()) return@runBlocking emptyList()

    val context = buildHomeFeedContext()
    val rawSubs = runCatching { youTubeRepository().getSubscriptionFeed(subIds.toList()) }.getOrDefault(emptyList())
    val pool = rawSubs
        .filter { it.serviceId == serviceId }
        .filterValid()
        .filterWatched(context.watched)
        .filter { it.channelId.isBlank() || it.channelId !in context.excludedChannels }
        .enrichAvatars(context.subAvatarMap)
    if (pool.isEmpty()) return@runBlocking emptyList()

    val ranked = runCatching { FlowNeuroEngine.rank(pool, subIds) }.getOrDefault(pool)
    val spaced = spaceByChannel(demoteByFit(ranked, context.taste), gap = 1)

    val result = spaced.map { it.toStreamInfoItem(serviceId) }
    homeFeedCache[cacheKey] = HomeFeedCacheEntry(result, now)
    result
}

/** "Load more": deeper discovery-query round, ranked like [buildAndRankHomeFeed]. Uncached, no
 * subscription-feed re-pull (subs lane is first-load only, matching native). Shares
 * FlowNeuroEngine's discovery-depth counter with native. */
fun HistoryDbHelper.continueDiscoveryFeed(serviceId: Int): Pair<List<InfoItem>, Boolean> = runBlocking {
    ensureFlowNeuroInitialized()
    val repo = youTubeRepository()

    val videos = fetchDiscoveryVideos(repo, resetDepth = false)
    val result = rankAndConvert(videos, serviceId)
    result to result.isNotEmpty()
}

// The Shorts feed is not served here. Native equivalents: ShortsRepository and ShortsDiscoveryEngine.

/** Trending kiosk, unranked - `handleApiHome()`'s raw JSON feed (vs. `handleApiRecommendations()`,
 * which uses [buildAndRankHomeFeed]'s ranked pool). No pagination (`YoutubeTrendingExtractor`
 * has no next page). */
fun HistoryDbHelper.fetchTrendingItems(serviceId: Int): List<StreamInfoItem> = runBlocking {
    trendingVideos().map { it.toStreamInfoItem(serviceId) }
}

/** YouTube's trending chart for the user's trending region. */
private suspend fun HistoryDbHelper.trendingVideos(): List<FlowVideo> {
    val region = playerPreferences().trendingRegion.first()
    return YouTube.videoCharts("TRENDING_VIDEOS", chartsCountryOrFallback(region)).getOrNull()?.entries.orEmpty()
}

/**
 * Reorders `items` via FlowNeuroEngine - never changes composition, only order. Falls back to
 * original order on failure.
 */
fun HistoryDbHelper.rankWithFlowNeuro(items: List<InfoItem>, serviceId: Int): List<InfoItem> {
    val streamItems = items.filterIsInstance<StreamInfoItem>()
    if (streamItems.isEmpty()) return items
    return try {
        runBlocking {
            ensureFlowNeuroInitialized()
            val userSubs = subscriptionRepository().getAllSubscriptionIds()

            val videoById = LinkedHashMap<String, StreamInfoItem>()
            val videos = ArrayList<FlowVideo>(streamItems.size)
            for (item in streamItems) {
                val video = item.toFlowVideo(serviceId)
                if (video.id.isNotBlank() && !videoById.containsKey(video.id)) {
                    videoById[video.id] = item
                    videos.add(video)
                }
            }
            if (videos.isEmpty()) return@runBlocking items

            val ranked = FlowNeuroEngine.rank(videos, userSubs)
            val rankedItems: List<InfoItem> = ranked.mapNotNull { videoById[it.id] }

            val rankedSet = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InfoItem, Boolean>())
            rankedSet.addAll(rankedItems)
            val leftovers = items.filter { it !in rankedSet }

            rankedItems + leftovers
        }
    } catch (e: Exception) {
        LocalHttpServer.log("FlowNeuro ranking failed, falling back to original order: " + e.message)
        items
    }
}

/** Reports a click/watch/etc. signal to FlowNeuroEngine. Best-effort. */
fun HistoryDbHelper.reportFlowNeuroInteraction(
    info: StreamInfo,
    serviceId: Int,
    type: InteractionType,
    percentWatched: Float = 0f,
) {
    try {
        runBlocking {
            ensureFlowNeuroInitialized()
            FlowNeuroEngine.onVideoInteraction(info.toFlowVideo(serviceId), type, percentWatched)
        }
    } catch (e: Exception) {
        LocalHttpServer.log("FlowNeuro interaction-signal error: " + e.message)
    }
}

/**
 * Related videos via `PlayerRelatedVideosPolicy`, same as native's watch page - never raw
 * `StreamInfo.relatedItems`. `primary` is the extractor's own list (no network call); falls back
 * to `getRelatedCandidates()` (InnerTube `/next`) only if `primary` sanitizes to empty.
 */
fun HistoryDbHelper.nativeRelatedVideos(info: StreamInfo, serviceId: Int): List<StreamInfoItem> {
    val videoId = LocalHttpServer.getVideoId(info.url)
    val repo = youTubeRepository()
    val shortsEnabled = !nativeHideShorts()
    val primary = repo.getRelatedVideosFromStreamInfo(info)
    val sanitizedPrimary = PlayerRelatedVideosPolicy.sanitize(videoId, primary, shortsEnabled)
    val selected = if (sanitizedPrimary.isNotEmpty()) {
        sanitizedPrimary
    } else {
        val fallback = runBlocking {
            runCatching { repo.getRelatedCandidates(videoId) }.getOrDefault(emptyList())
        }
        PlayerRelatedVideosPolicy.select(videoId, primary, fallback, current = emptyList(), shortsEnabled = shortsEnabled)
    }
    return selected.map { it.toStreamInfoItem(serviceId) }
}

/** History rows for the JSON API: the fields of a video, plus how far the viewer got (0-100). */
fun HistoryDbHelper.nativeHistoryJson(): org.json.JSONArray = runBlocking {
    val array = org.json.JSONArray()
    for (entry in viewHistory().getAllHistory().first()) {
        val json = org.json.JSONObject()
        json.put("id", entry.videoId)
        json.put("url", videoIdToUrl(entry.videoId, entry.serviceId))
        json.put("serviceId", entry.serviceId)
        json.put("title", entry.title)
        json.put("channelName", entry.channelName)
        json.put("channelId", if (entry.channelId.isNotEmpty()) channelIdToUrl(entry.channelId, entry.serviceId) else "")
        json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(entry.thumbnailUrl))
        json.put("channelThumbnailUrl", "")
        json.put("duration", (entry.duration / 1000).toInt())
        json.put("viewCount", -1)
        json.put("uploadDate", "")
        json.put("isLive", false)
        json.put("isShort", entry.isShort)
        json.put("progress", entry.progressPercentage.toInt().coerceIn(0, 100))
        array.put(json)
    }
    array
}
