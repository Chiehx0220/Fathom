package io.github.aedev.flow.service

import androidx.media3.common.Player

/**
 * Whether a media service stays up when Flow is swiped away from recents. Media3's own rule keeps it
 * only while a session is playing; this also keeps it while a requested song or video is still
 * loading, so a swipe between tracks or during a stall does not end playback. A pause ends it, and so
 * does every swipe for a viewer who turned [keepPlayingAfterSwipe] off (#1468).
 */
internal fun keepsPlayingOnTaskRemoved(
    keepPlayingAfterSwipe: Boolean,
    playbackOngoing: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    hasMedia: Boolean,
): Boolean =
    keepPlayingAfterSwipe &&
        playbackOngoing &&
        hasMedia &&
        playWhenReady &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED

internal fun Player.keepsPlayingOnTaskRemoved(
    keepPlayingAfterSwipe: Boolean,
    playbackOngoing: Boolean,
): Boolean =
    keepsPlayingOnTaskRemoved(
        keepPlayingAfterSwipe = keepPlayingAfterSwipe,
        playbackOngoing = playbackOngoing,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        hasMedia = mediaItemCount > 0,
    )
