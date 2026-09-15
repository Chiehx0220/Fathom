package io.github.aedev.flow.data.model

import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

val Video.isYouTube: Boolean
    get() = serviceId == ServiceList.YouTube.serviceId

/** Same check as [Video.isYouTube], for call sites that only have the [StreamingService]. */
val StreamingService.isYouTube: Boolean
    get() = serviceId == ServiceList.YouTube.serviceId

/** Same check as [Video.isYouTube], for call sites that only have a raw serviceId. */
val Int.isYouTubeServiceId: Boolean
    get() = this == ServiceList.YouTube.serviceId

/** See [serviceSupportsBulletComments] for why this isn't a NewPipeExtractor capability check. */
val Video.supportsBulletComments: Boolean
    get() = serviceSupportsBulletComments(serviceId)
