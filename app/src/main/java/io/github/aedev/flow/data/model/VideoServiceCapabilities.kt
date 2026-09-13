package io.github.aedev.flow.data.model

import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.ServiceList

val Video.isYouTube: Boolean
    get() = serviceId == ServiceList.YouTube.serviceId

/** See [serviceSupportsBulletComments] for why this isn't a NewPipeExtractor capability check. */
val Video.supportsBulletComments: Boolean
    get() = serviceSupportsBulletComments(serviceId)
