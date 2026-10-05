package io.github.aedev.flow.data.repository

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.ServiceList

/** False for another service's id: sent to YouTube it only draws a 404. */
internal val String.isYouTubeVideoId: Boolean get() = serviceIdOfVideo(this, ServiceList.YouTube.serviceId).isYouTubeServiceId
