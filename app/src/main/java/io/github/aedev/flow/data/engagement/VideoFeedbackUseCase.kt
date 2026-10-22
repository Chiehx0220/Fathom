package io.github.aedev.flow.data.engagement

import io.github.aedev.flow.data.feed.BlockedContentStore
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.WatchLaterCleanup
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.BlockedChannelNames
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.stats.LedgerAction
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import javax.inject.Inject

/** A channel a block applied to, named as the viewer knows it. */
data class BlockedChannel(
    val channelId: String,
    val channelName: String,
)

/** Video menu actions: watch later, not interested, watched, channel block. */
class VideoFeedbackUseCase
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val playlistRepository: PlaylistRepository,
        private val viewHistory: ViewHistory,
        private val videoStats: VideoStatsRecorder,
        private val watchLaterCleanup: WatchLaterCleanup,
        private val blockedNames: BlockedChannelNames,
        private val blockedContent: BlockedContentStore,
    ) {
        /** Adds [video] to Watch later, or takes it out; returns whether it is saved afterwards. */
        suspend fun toggleWatchLater(video: Video): Boolean {
            if (playlistRepository.isInWatchLater(video.id)) {
                playlistRepository.removeFromWatchLater(video.id)
                return false
            }
            playlistRepository.addToWatchLater(video)
            videoStats.onAction(LedgerAction.SAVE)
            return true
        }

        suspend fun setWatchLater(
            video: Video,
            saved: Boolean,
        ) {
            if (saved) playlistRepository.addToWatchLater(video) else playlistRepository.removeFromWatchLater(video.id)
        }

        /**
         * Hides [video]'s channel from every feed. A live or premiere entry can carry a handle instead
         * of a channel id, so that case asks the watch page for the real id first.
         */
        suspend fun blockChannel(video: Video): BlockedChannel {
            val metadata = if (video.channelId.startsWith("UC")) null else repository.getLiveWatchMetadata(video.id)
            val channelId = metadata?.channelId.orEmpty().ifBlank { video.channelId }
            check(channelId.isNotBlank()) { "No channel id for ${video.id}" }
            val channelName = metadata?.channelName.orEmpty().ifBlank { video.channelName }
            blockedContent.blockChannel(channelId)
            blockedNames.remember(channelId, channelName)
            videoStats.onAction(LedgerAction.BLOCK_CHANNEL)
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.ChannelBlocked(channelId, video.id))
            return BlockedChannel(channelId, channelName)
        }

        suspend fun unblockChannel(channelId: String) {
            blockedContent.unblockChannel(channelId)
            blockedNames.forget(channelId)
        }

        suspend fun markNotInterested(video: Video) {
            blockedContent.markNotInterested(video.id)
            videoStats.onAction(LedgerAction.NOT_INTERESTED)
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.NotInterested(video.id, video.channelId))
        }

        /** Marks [video] watched to the end. */
        suspend fun markWatched(video: Video) {
            val durationMs = if (video.duration > 0) video.duration * 1000L else 1000L
            viewHistory.savePlaybackPosition(
                videoId = video.id,
                position = durationMs,
                duration = durationMs,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl.ifEmpty { ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(video.id) },
                channelName = video.channelName,
                channelId = video.channelId,
                isMusic = false,
                isShort = video.isShort,
            )
            FeedInvalidationBus.emit(FeedInvalidationBus.Event.MarkedWatched(video.id))
            watchLaterCleanup.onFinished(video.id)
        }

        /** Clears the watched progress of [video]; the history row stays. */
        suspend fun markUnwatched(video: Video) {
            viewHistory.clearProgress(video.id)
        }
    }
