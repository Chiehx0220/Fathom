package io.github.aedev.flow.data.model

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.content.ServiceIds
import io.github.aedev.flow.player.stream.serviceSupportsBulletComments

/**
 * Owning service: id shape decides (pre-service-id rows carry 0; Bilibili id never a YouTube id); [Video.serviceId] fallback for shapeless
 * ids.
 */
val Video.resolvedServiceId: Int
    get() = serviceIdOfVideo(id, serviceId)

val Video.isYouTube: Boolean
    get() = resolvedServiceId.isYouTubeServiceId

/** [Video.isYouTube] for call sites holding a resolved serviceId. */
val Int.isYouTubeServiceId: Boolean
    get() = this == ServiceIds.YOUTUBE

/** See [serviceSupportsBulletComments]: a per-service capability check. */
val Video.supportsBulletComments: Boolean
    get() = serviceSupportsBulletComments(resolvedServiceId)
