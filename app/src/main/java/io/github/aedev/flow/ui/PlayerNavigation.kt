package io.github.aedev.flow.ui

import android.net.Uri
import androidx.navigation.NavHostController
import io.github.aedev.flow.player.stream.PlaybackPrefetcher
import org.schabi.newpipe.extractor.ServiceList

/**
 * Opens the player for [videoId] on [serviceId] (0 = YouTube, the default), warming stream
 * extraction before navigating.
 *
 * The extraction started here is joined — not duplicated — by the player's own load path, so the
 * only effect is that navigation, composition and player setup overlap with the network work
 * instead of queuing behind it. Use this for every user-initiated open of the player screen.
 */
internal fun NavHostController.navigateToPlayer(
    videoId: String,
    serviceId: Int = ServiceList.YouTube.serviceId,
) {
    PlaybackPrefetcher.prefetch(videoId)
    // videoId is encoded because non-YouTube ids can contain characters the route's own
    // "?serviceId=" query syntax would otherwise misparse (e.g. Bilibili's "BVxxxx?p=1").
    navigate("player/${Uri.encode(videoId)}?serviceId=$serviceId")
}
