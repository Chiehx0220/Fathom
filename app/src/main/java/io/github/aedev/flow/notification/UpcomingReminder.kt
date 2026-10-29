package io.github.aedev.flow.notification

import kotlinx.serialization.Serializable

/** A premiere reminder as the profile keeps it, so it can be queued again after a switch cancelled it. */
@Serializable
data class UpcomingReminder(
    val videoId: String,
    val releaseTimeMs: Long,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
)
