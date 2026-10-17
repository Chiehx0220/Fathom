package io.github.aedev.flow

import io.github.aedev.flow.bilibili.BilibiliDeepLink
import io.github.aedev.flow.bilibili.BilibiliLinkTarget
import io.github.aedev.flow.ui.youtubeChannelRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [MainActivity] hook. */
internal object BilibiliLinks {
    /** Bilibili video/uploader link handler: shared text, b23.tv short links. */
    fun open(
        text: String,
        scope: CoroutineScope,
        onVideo: (String) -> Unit,
        onRoute: (String?) -> Unit,
    ): Boolean {
        fun open(target: BilibiliLinkTarget) {
            when (target) {
                is BilibiliLinkTarget.Video -> {
                    onVideo(target.videoId)
                }

                is BilibiliLinkTarget.Uploader -> {
                    onRoute(youtubeChannelRoute(target.mid.toString()))
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
