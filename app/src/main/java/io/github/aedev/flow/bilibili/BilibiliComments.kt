/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BilibiliCommentExtractor.java, BilibiliCommentsInfoItemExtractor.java
 * and linkHandler/BilibiliCommentsLinkHandlerFactory.java.
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A video's comments: hot-ordered pages with the pinned ones first, and the replies under one comment.
 *
 * A closed comment section answers with an error code and is returned as empty, not as a failure.
 */
internal class BilibiliComments(
    private val session: BilibiliSession,
    private val json: Json,
) {
    /** [offset] is the previous page's [BilibiliCommentsPage.nextOffset]; empty for the first page. */
    suspend fun page(
        bvid: String,
        offset: String,
    ): BilibiliCommentsPage {
        val aid = BilibiliSigning.bv2av(bvid)
        val body =
            session.guardedGet(json, videoUrl(bvid), COMMENTS_CLOSED_CODES) {
                val params = LinkedHashMap<String, String>()
                params["oid"] = aid.toString()
                params["type"] = "1"
                params["mode"] = "3"
                params["pagination_str"] = """{"offset":"${escapeJson(offset)}"}"""
                params["plat"] = "1"
                params["web_location"] = "1315875"
                session.signedUrl(COMMENTS_URL, params)
            }
        val data = decode(body).data ?: return BilibiliCommentsPage(emptyList(), null)
        val pinned = data.topReplies.orEmpty().map { it.toComment(isPinned = true) }
        val comments = pinned + data.replies.orEmpty().map { it.toComment(isPinned = false) }
        val next = data.cursor.paginationReply.nextOffset
        return BilibiliCommentsPage(comments, next.takeIf { !data.cursor.isEnd && it.isNotBlank() && comments.isNotEmpty() })
    }

    /** [page] is 1-based; a page shorter than [REPLIES_PAGE_SIZE] is the last one. */
    suspend fun replies(
        bvid: String,
        rpid: String,
        page: Int,
    ): BilibiliRepliesPage {
        val aid = BilibiliSigning.bv2av(bvid)
        // pn has to come last or nothing is returned.
        val url = "$REPLIES_URL$aid&root=$rpid&pn=$page"
        val body = session.guardedGet(json, videoUrl(bvid), COMMENTS_CLOSED_CODES) { url }
        val replies =
            decode(body)
                .data
                ?.replies
                .orEmpty()
                .map { it.toComment(isPinned = false) }
        return BilibiliRepliesPage(replies, (page + 1).takeIf { replies.size >= REPLIES_PAGE_SIZE })
    }

    private fun CommentsResponse.Reply.toComment(isPinned: Boolean) =
        BilibiliComment(
            id = rpidStr,
            authorMid = mid,
            authorName = member.uname,
            authorAvatarUrl = member.avatar.toHttps(),
            text = unescapeHtml(content.message),
            likeCount = like,
            postedSec = ctime,
            replyCount = rcount,
            isPinned = isPinned,
            isLikedByUploader = upAction.like,
        )

    private fun decode(body: String): CommentsResponse =
        try {
            json.decodeFromString(body)
        } catch (e: SerializationException) {
            throw BilibiliContentNotAvailableException("Unreadable Bilibili comments: ${e.message}")
        }

    private fun videoUrl(bvid: String) = "https://www.bilibili.com/video/$bvid"

    private fun escapeJson(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescapeHtml(text: String) =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")

    private companion object {
        const val COMMENTS_URL = "https://api.bilibili.com/x/v2/reply/wbi/main"
        const val REPLIES_URL = "https://api.bilibili.com/x/v2/reply/reply?type=1&ps=10&web_location=333.788&oid="
        const val REPLIES_PAGE_SIZE = 10

        /** 12002: comments closed. 12061: only the uploader's followers may see them. */
        val COMMENTS_CLOSED_CODES = setOf(0, 12002, 12061)
    }
}
