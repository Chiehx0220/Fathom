package io.github.aedev.flow.data.model

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

/**
 * The service this video belongs to. The id's shape decides: a row saved before Bilibili had its own
 * service id carries 0, and a Bilibili id can never be a YouTube one. [Video.serviceId] is only the
 * fallback for ids whose shape says nothing.
 */
val Video.resolvedServiceId: Int
    get() = serviceIdOfVideo(id, serviceId)

val Video.isYouTube: Boolean
    get() = resolvedServiceId.isYouTubeServiceId

/** Same check as [Video.isYouTube], for call sites that only have the [StreamingService]. */
val StreamingService.isYouTube: Boolean
    get() = serviceId == ServiceList.YouTube.serviceId

/** Same check as [Video.isYouTube], for call sites that only have an already resolved serviceId. */
val Int.isYouTubeServiceId: Boolean
    get() = this == ServiceList.YouTube.serviceId

/** See [serviceSupportsBulletComments] for why this isn't a NewPipeExtractor capability check. */
val Video.supportsBulletComments: Boolean
    get() = serviceSupportsBulletComments(serviceId)
