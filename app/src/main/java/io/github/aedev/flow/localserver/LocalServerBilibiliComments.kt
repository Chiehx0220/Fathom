package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliDanmaku
import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page

/**
 * The local server's Bilibili comments and danmaku. The continuation of a comment page is a [Page]
 * whose url holds "c:<offset>", and the one of the replies under a comment "r:<rpid>:<page>".
 */
internal object LocalServerBilibiliComments {
    fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        val bvid = BilibiliVideoId.parse(LocalServerBilibili.videoIdOf(videoUrl)).first
        val api = bilibiliApi(context)
        val token = nextPage?.url.orEmpty()
        if (token.startsWith("r:")) {
            val (_, rpid, pn) = token.split(':')
            val replies = runBlocking { api.commentReplies(bvid, rpid, pn.toIntOrNull() ?: 1) }
            return CommentsResult(
                replies.replies.map { it.toCommentItem(videoUrl, hasReplies = false) },
                replies.nextPage?.let { Page("r:$rpid:$it") },
            )
        }
        val offset = token.removePrefix("c:")
        val page = runBlocking { api.comments(bvid, offset) }
        return CommentsResult(page.comments.map { it.toCommentItem(videoUrl, hasReplies = true) }, page.nextOffset?.let { Page("c:$it") })
    }

    /** The video's danmaku as the client-side overlay reads it; empty when there is none. */
    fun danmaku(
        context: Context,
        mediaUrl: String,
    ): JSONArray {
        val api = bilibiliApi(context)
        val (bvid, part) = BilibiliVideoId.parse(LocalServerBilibili.videoIdOf(mediaUrl))
        val danmaku =
            runBlocking {
                val info = api.videoInfo(bvid, part)
                api.danmaku(info)
            }
        return JSONArray().also { array -> danmaku.forEach { array.put(it.toJson()) } }
    }

    private fun BilibiliDanmaku.toJson() =
        JSONObject()
            .put("text", text)
            .put("time", timeMs / 1000.0)
            .put("color", String.format("#%06X", argbColor and 0xFFFFFF))
            .put(
                "position",
                when (position) {
                    BilibiliDanmakuPosition.TOP -> "top"
                    BilibiliDanmakuPosition.BOTTOM -> "bottom"
                    BilibiliDanmakuPosition.SCROLL -> "scroll"
                },
            ).put("size", relativeFontSize)
}
