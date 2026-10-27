package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliDanmaku
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.Page

/** Comments + danmaku. Continuation [Page] url: "c:<offset>"; replies "r:<rpid>:<page>". */
internal object LocalServerBilibiliComments {
    fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        val bvid = BilibiliRef.Video.parse(LocalServerBilibili.videoIdOf(videoUrl)).bvid
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

    /** Video danmaku for the client-side overlay; empty if none. */
    fun danmaku(
        context: Context,
        mediaUrl: String,
    ): List<Bullet> {
        val api = bilibiliApi(context)
        val ref = BilibiliRef.Video.parse(LocalServerBilibili.videoIdOf(mediaUrl))
        val danmaku =
            runBlocking {
                val info = api.videoInfo(ref.bvid, ref.part)
                api.danmaku(info)
            }
        return danmaku.map { it.toBullet() }
    }

    private fun BilibiliDanmaku.toBullet() =
        Bullet(text, String.format("#%06X", argbColor and 0xFFFFFF), position.webName(), timeMs / 1000.0, relativeFontSize)
}
