package io.github.aedev.flow.player

import io.github.aedev.flow.data.feed.FeedExclusions

/** What the viewer hid, so autoplay picked outside the player screen skips it too. */
fun interface FeedExclusionsSource {
    suspend fun current(): FeedExclusions
}
