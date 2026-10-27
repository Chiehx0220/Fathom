package io.github.aedev.flow.data.model

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

/**
 * Owning service: id shape decides (pre-service-id rows carry 0; Bilibili id never a YouTube id); [Video.serviceId] fallback for shapeless
 * ids.
 */
val Video.resolvedServiceId: Int
    get() = serviceIdOfVideo(id, serviceId)

val Video.isYouTube: Boolean
    get() = resolvedServiceId.isYouTubeServiceId

/** [Video.isYouTube] for call sites holding a [StreamingService]. */
val StreamingService.isYouTube: Boolean
    get() = serviceId == ServiceList.YouTube.serviceId

/** [Video.isYouTube] for call sites holding a resolved serviceId. */
val Int.isYouTubeServiceId: Boolean
    get() = this == ServiceList.YouTube.serviceId

/** See [serviceSupportsBulletComments]: not a NewPipeExtractor capability check. */
val Video.supportsBulletComments: Boolean
    get() = serviceSupportsBulletComments(resolvedServiceId)
