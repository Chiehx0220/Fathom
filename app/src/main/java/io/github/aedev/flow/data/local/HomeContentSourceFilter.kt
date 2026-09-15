package io.github.aedev.flow.data.local

import org.schabi.newpipe.extractor.ServiceList

/**
 * Which service's uploads Home's fresh-subs/recommendation lanes show. Subscriptions and search
 * are unaffected - this only narrows what Home itself surfaces.
 */
enum class HomeContentSourceFilter {
    MIX,
    YOUTUBE,
    BILIBILI,
    ;

    /** Null means no filtering (MIX) - every service's videos pass. */
    val serviceId: Int?
        get() =
            when (this) {
                MIX -> null
                YOUTUBE -> ServiceList.YouTube.serviceId
                BILIBILI -> ServiceList.BiliBili.serviceId
            }

    companion object {
        fun fromStoredName(name: String?): HomeContentSourceFilter = entries.firstOrNull { it.name == name } ?: MIX
    }
}
