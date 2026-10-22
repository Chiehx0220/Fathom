package io.github.aedev.flow.utils

import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.ServiceList

/** Video id to canonical YouTube or Bilibili URL; a full link is accepted as the id. */
fun videoIdToUrl(
    videoId: String,
    serviceId: Int = ServiceList.YouTube.serviceId,
): String {
    val id = bareVideoId(videoId)
    return if (serviceIdOfVideo(id, serviceId).isYouTubeServiceId) "https://www.youtube.com/watch?v=$id" else BilibiliVideoId.toUrl(id)
}

private fun bareVideoId(value: String): String =
    when {
        "v=" in value -> value.substringAfter("v=").substringBefore('&')
        "/shorts/" in value -> value.substringAfter("/shorts/").substringBefore('?')
        "youtu.be/" in value -> value.substringAfter("youtu.be/").substringBefore('?')
        else -> BilibiliVideoId.fromUrl(value) ?: BilibiliLiveId.fromUrl(value) ?: value
    }
