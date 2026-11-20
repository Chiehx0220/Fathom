package io.github.aedev.flow.data.repository

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.content.ServiceIds
import io.github.aedev.flow.data.model.isYouTubeServiceId

/** False for another service's id (YouTube 404). */
internal val String.isYouTubeVideoId: Boolean get() = serviceIdOfVideo(this, ServiceIds.YOUTUBE).isYouTubeServiceId
