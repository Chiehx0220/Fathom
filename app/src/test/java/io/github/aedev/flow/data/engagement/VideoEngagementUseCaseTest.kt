package io.github.aedev.flow.data.engagement

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pins the engagement sequence every surface shares: the local write lands, then the caller is told. */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoEngagementUseCaseTest {
    private val testDispatcher = StandardTestDispatcher()

    private val isSubscribed = MutableStateFlow(false)
    private val subscription = MutableStateFlow<ChannelSubscription?>(null)
    private val likeState = MutableStateFlow<String?>(null)

    private val subscriptionRepository: SubscriptionRepository = mockk(relaxed = true)
    private val likedVideosRepository: LikedVideosRepository = mockk(relaxed = true)

    private val useCase =
        VideoEngagementUseCase(
            subscriptionRepository = subscriptionRepository,
            likedVideosRepository = likedVideosRepository,
            videoStats = mockk(relaxed = true),
        )

    private val order = mutableListOf<String>()

    init {
        every { subscriptionRepository.isSubscribed(any()) } returns isSubscribed
        every { subscriptionRepository.getSubscription(any()) } returns subscription
        every { likedVideosRepository.getLikeState(any()) } returns likeState
    }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Title $id",
            channelName = "Channel $id",
            channelId = "channel_$id",
            thumbnailUrl = "https://example.invalid/$id.jpg",
            duration = 120,
            viewCount = 1L,
            uploadDate = "2026-01-01",
        )

    @Test
    fun `subscribing writes the channel and reports it`() =
        runTest(testDispatcher) {
            isSubscribed.value = false
            val written = slot<ChannelSubscription>()

            useCase.toggleSubscription("ch_1", "Channel One", "avatar.jpg") { order += "applied:$it" }

            coVerify(exactly = 1) { subscriptionRepository.subscribe(capture(written)) }
            assertThat(written.captured.channelId).isEqualTo("ch_1")
            assertThat(written.captured.channelName).isEqualTo("Channel One")
            assertThat(written.captured.channelThumbnail).isEqualTo("avatar.jpg")
            coVerify(exactly = 0) { subscriptionRepository.unsubscribe(any()) }
            assertThat(order).containsExactly("applied:true")
        }

    @Test
    fun `unsubscribing removes the channel and reports it`() =
        runTest(testDispatcher) {
            isSubscribed.value = true

            useCase.toggleSubscription("ch_1", "Channel One", "avatar.jpg") { order += "applied:$it" }

            coVerify(exactly = 1) { subscriptionRepository.unsubscribe("ch_1") }
            coVerify(exactly = 0) { subscriptionRepository.subscribe(any()) }
            assertThat(order).containsExactly("applied:false")
        }

    @Test
    fun `applySubscription writes the caller's decision without reading the stored state`() =
        runTest(testDispatcher) {
            isSubscribed.value = true

            useCase.applySubscription("ch_1", "Channel One", "avatar.jpg", subscribed = true)

            coVerify(exactly = 1) { subscriptionRepository.subscribe(any()) }
            coVerify(exactly = 0) { subscriptionRepository.unsubscribe(any()) }
            verify(exactly = 0) { subscriptionRepository.isSubscribed(any()) }
        }

    @Test
    fun `setNotificationEnabled writes through to the subscription repository`() =
        runTest(testDispatcher) {
            useCase.setNotificationEnabled("ch_1", enabled = true)

            coVerify(exactly = 1) { subscriptionRepository.updateNotificationState("ch_1", true) }
        }

    @Test
    fun `liking stores the video and reports it`() =
        runTest(testDispatcher) {
            val stored = slot<LikedVideoInfo>()

            useCase.like(video("vid_1")) { order += "applied" }

            coVerify(exactly = 1) { likedVideosRepository.likeVideo(capture(stored)) }
            assertThat(stored.captured.videoId).isEqualTo("vid_1")
            assertThat(stored.captured.title).isEqualTo("Title vid_1")
            assertThat(stored.captured.thumbnail).isEqualTo("https://example.invalid/vid_1.jpg")
            assertThat(order).containsExactly("applied")
        }

    @Test
    fun `disliking stores the state`() =
        runTest(testDispatcher) {
            useCase.dislike("vid_1")

            coVerify(exactly = 1) { likedVideosRepository.dislikeVideo("vid_1") }
        }

    @Test
    fun `removeLike clears the stored state`() =
        runTest(testDispatcher) {
            useCase.removeLike("vid_1")

            coVerify(exactly = 1) { likedVideosRepository.removeLikeState("vid_1") }
        }

    @Test
    fun `engagement holds one collector per concern and reports every change`() =
        runTest(testDispatcher) {
            val scope = CoroutineScope(testDispatcher)
            val seen = mutableListOf<VideoEngagement>()
            val job = scope.launch { useCase.engagement("vid_1", "ch_1").collect { seen += it } }
            advanceUntilIdle()

            assertThat(isSubscribed.subscriptionCount.value).isEqualTo(1)
            assertThat(subscription.subscriptionCount.value).isEqualTo(1)
            assertThat(likeState.subscriptionCount.value).isEqualTo(1)
            assertThat(seen).containsExactly(VideoEngagement())

            isSubscribed.value = true
            subscription.value =
                ChannelSubscription(
                    channelId = "ch_1",
                    channelName = "Channel One",
                    channelThumbnail = "",
                    isNotificationEnabled = true,
                )
            likeState.value = "LIKED"
            advanceUntilIdle()

            assertThat(seen.last()).isEqualTo(
                VideoEngagement(isSubscribed = true, isNotificationEnabled = true, likeState = "LIKED"),
            )
            verify(exactly = 1) { subscriptionRepository.isSubscribed("ch_1") }
            verify(exactly = 1) { subscriptionRepository.getSubscription("ch_1") }
            verify(exactly = 1) { likedVideosRepository.getLikeState("vid_1") }
            job.cancel()
        }
}
