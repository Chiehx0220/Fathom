package io.github.aedev.flow.data.feed

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class FeedExclusionsTest {
    private fun video(
        id: String,
        channelId: String,
    ) = Video(
        id = id,
        title = "title-$id",
        channelName = "channel-$channelId",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1,
        uploadDate = "1 day ago",
    )

    @Test
    fun `a not interested video and a blocked channel are hidden`() {
        val exclusions = FeedExclusions(suppressedVideoIds = setOf("v1"), blockedChannelIds = setOf("ch-b"))

        assertThat(exclusions.hides(video("v1", "ch-a"))).isTrue()
        assertThat(exclusions.hides(video("v2", "ch-b"))).isTrue()
        assertThat(exclusions.hides(video("v2", "ch-a"))).isFalse()
    }

    @Test
    fun `a blank channel id is never treated as blocked`() {
        assertThat(FeedExclusions(blockedChannelIds = setOf("")).hidesChannel("")).isFalse()
    }

    @Test
    fun `hiding more channels keeps what was already hidden`() {
        val exclusions = FeedExclusions(suppressedVideoIds = setOf("v1"), blockedChannelIds = setOf("ch-a")).hidingChannels(setOf("ch-b"))

        assertThat(exclusions.suppressedVideoIds).containsExactly("v1")
        assertThat(exclusions.blockedChannelIds).containsExactly("ch-a", "ch-b")
    }

    @Test
    fun `hiding no channels returns the same exclusions`() {
        val exclusions = FeedExclusions(blockedChannelIds = setOf("ch-a"))

        assertThat(exclusions.hidingChannels(emptySet())).isSameInstanceAs(exclusions)
        assertThat(FeedExclusions.NONE.isEmpty).isTrue()
    }
}
