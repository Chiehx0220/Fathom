package io.github.aedev.flow.utils

import org.schabi.newpipe.extractor.StreamingService

/**
 * A non-YouTube service's channel ids and URLs don't fit any YouTube-shaped pattern - every caller
 * that branches on service ends up hand-rolling
 * `runCatching { service.channelLHFactory.getX(...) }.getOrElse { ... }`. Written once so each
 * caller only supplies its own fallback for when the link handler can't parse this one.
 */
fun resolveNonYouTubeChannelId(
    url: String,
    service: StreamingService,
    fallback: () -> String,
): String = runCatching { service.channelLHFactory.getId(url) }.getOrElse { fallback() }

/** @see resolveNonYouTubeChannelId */
fun resolveNonYouTubeChannelUrl(
    channelId: String,
    service: StreamingService,
    fallback: () -> String,
): String = runCatching { service.channelLHFactory.getUrl(channelId) }.getOrElse { fallback() }

/** Same idea as [resolveNonYouTubeChannelId], for a stream (video) id instead of a channel id. */
fun resolveNonYouTubeStreamId(
    url: String,
    service: StreamingService,
    fallback: () -> String,
): String = runCatching { service.streamLHFactory.getId(url) }.getOrElse { fallback() }

/** Same idea as [resolveNonYouTubeChannelId], for a playlist id instead of a channel id. */
fun resolveNonYouTubePlaylistId(
    url: String,
    service: StreamingService,
    fallback: () -> String,
): String = runCatching { service.playlistLHFactory.getId(url) }.getOrElse { fallback() }
