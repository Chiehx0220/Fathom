package io.github.aedev.flow.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.search.SearchServiceSwitcher

/**
 * Video id of a pasted Bilibili video / b23.tv link, or null; [parseYouTubeLink] covers YouTube shapes: only the rest here.
 */
internal fun resolvePastedBilibiliVideoLink(url: String): String? {
    if (!url.startsWith("http")) return null
    return BilibiliVideoId.fromUrl(url)
}

internal fun sharedBilibiliVideo(
    videoId: String,
    title: String,
) = Video(
    id = videoId,
    title = title,
    channelName = title,
    channelId = "",
    thumbnailUrl = "",
    duration = 0,
    viewCount = 0L,
    uploadDate = "",
    serviceId = BILIBILI_SERVICE_ID,
)

/** Pasted Bilibili link to [onVideoClick], else [submit]. */
@Composable
internal fun rememberBilibiliLinkSubmitter(
    submit: (String, SearchFilter) -> Unit,
    onVideoClick: (Video) -> Unit,
): (String, SearchFilter) -> Unit {
    val context = LocalContext.current
    return remember(submit, onVideoClick, context) {
        { raw, filters ->
            val videoId = resolvePastedBilibiliVideoLink(raw.trim())
            if (videoId != null) {
                onVideoClick(sharedBilibiliVideo(videoId, context.getString(R.string.shared_video)))
            } else {
                submit(raw, filters)
            }
        }
    }
}

/** YouTube/Bilibili switch under the search bar. */
@Composable
internal fun SearchServiceRow(
    viewModel: SearchViewModel,
    state: SearchScreenState,
    serviceId: Int,
) {
    SearchServiceSwitcher(
        selectedServiceId = serviceId,
        onServiceSelected = {
            state.stopTyping()
            viewModel.setService(it)
        },
    )
}
