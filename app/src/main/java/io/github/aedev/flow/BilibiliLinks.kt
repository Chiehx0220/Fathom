package io.github.aedev.flow

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliDeepLink
import io.github.aedev.flow.bilibili.BilibiliLinkTarget
import io.github.aedev.flow.ui.PendingDeeplink
import io.github.aedev.flow.ui.youtubeChannelRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Opens a Bilibili link for [MainActivity], so that upstream file only carries a call. */
internal object BilibiliLinks {
    /**
     * Opens a Bilibili video or uploader link, also inside a shared text or behind a b23.tv short link.
     * Returns false when [text] has none.
     */
    fun open(
        text: String,
        scope: CoroutineScope,
        onVideo: (PendingDeeplink) -> Unit,
        onRoute: (String?) -> Unit,
    ): Boolean {
        fun open(target: BilibiliLinkTarget) {
            when (target) {
                is BilibiliLinkTarget.Video -> {
                    onVideo(PendingDeeplink(videoId = target.videoId, serviceId = BILIBILI_SERVICE_ID))
                }

                is BilibiliLinkTarget.Uploader -> {
                    onRoute(youtubeChannelRoute(target.mid.toString(), BILIBILI_SERVICE_ID))
                }
            }
        }
        BilibiliDeepLink.parse(text)?.let {
            open(it)
            return true
        }
        if (!BilibiliDeepLink.isShortLink(text)) return false
        scope.launch { BilibiliDeepLink.resolve(text)?.let { open(it) } }
        return true
    }
}
