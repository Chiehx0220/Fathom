package io.github.aedev.flow.data.local

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.Video
import org.schabi.newpipe.extractor.ServiceList

/** Service whose uploads Home's fresh-subs/recommendation lanes show. Subscriptions, search unaffected. */
enum class HomeContentSourceFilter {
    MIX,
    YOUTUBE,
    BILIBILI,
    ;

    /** Null = MIX, no filtering. */
    val serviceId: Int?
        get() =
            when (this) {
                MIX -> null
                YOUTUBE -> ServiceList.YouTube.serviceId
                BILIBILI -> BILIBILI_SERVICE_ID
            }

    val isMix: Boolean get() = this == MIX

    fun allows(serviceId: Int): Boolean = this.serviceId?.let { it == serviceId } ?: true

    fun allows(video: Video): Boolean = allows(video.serviceId)

    companion object {
        fun fromStoredName(name: String?): HomeContentSourceFilter = entries.firstOrNull { it.name == name } ?: YOUTUBE
    }
}
